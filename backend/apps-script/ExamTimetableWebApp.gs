/**
 * JustPass Exam Timetable Web App
 *
 * Deployed from a @psgitech.ac.in account as an internal Workspace app.
 * When a student visits the URL, it:
 *   1. Searches THEIR Gmail for exam timetable emails
 *   2. Parses PDF attachments via Drive OCR
 *   3. Uploads parsed data to Firestore (shared for all users)
 *   4. Returns the timetable JSON
 *   5. Sets up a background trigger to auto-sync future emails
 *
 * Deploy: Web app → Execute as "User accessing the web app" → Access "Anyone in psgitech.ac.in"
 */

// ============ CONFIGURATION ============

var FIRESTORE_PROJECT = 'attendancewidget-63f89';
var FIRESTORE_COLLECTION = 'examTimetables';

var SESSION_TIMINGS = {
  'FN-I': '8:30 AM - 10:15 AM',
  'FN-II': '10:45 AM - 12:30 PM',
  'AN-I': '1:30 PM - 3:15 PM',
  'AN-II': '3:30 PM - 5:15 PM',
  'FN': '8:30 AM - 12:30 PM',
  'AN': '1:30 PM - 5:15 PM'
};

// ============ WEB APP ENTRY POINT ============

function doGet(e) {
  var action = (e && e.parameter && e.parameter.action) || 'sync';

  try {
    if (action === 'sync') {
      // Scan Gmail, parse PDFs, upload to Firestore, return results
      var result = scanAndSync_();

      // Set up background trigger (if not already)
      ensureTrigger_();

      return jsonResponse_({ success: true, data: result });
    }

    if (action === 'status') {
      return jsonResponse_({
        success: true,
        user: Session.getActiveUser().getEmail(),
        triggerActive: ScriptApp.getProjectTriggers().length > 0
      });
    }

    return jsonResponse_({ success: false, error: 'Unknown action' });
  } catch (err) {
    return jsonResponse_({ success: false, error: err.message });
  }
}

function jsonResponse_(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

// ============ GMAIL SCANNING ============

function scanAndSync_() {
  // Search for exam timetable emails
  var queries = [
    'subject:(CAT timetable "time table") has:attachment filename:pdf newer_than:30d',
    'subject:("Continuous Assessment" timetable) has:attachment filename:pdf newer_than:30d',
    'subject:(exam timetable schedule) from:(@psgtech.ac.in OR @psgitech.ac.in) has:attachment filename:pdf newer_than:30d',
    'subject:("end semester" timetable) has:attachment filename:pdf newer_than:30d',
    'subject:("model exam" timetable) has:attachment filename:pdf newer_than:30d'
  ];

  var allEntries = [];
  var processedSubjects = {};

  for (var q = 0; q < queries.length; q++) {
    try {
      var threads = GmailApp.search(queries[q], 0, 5);
      for (var t = 0; t < threads.length; t++) {
        var messages = threads[t].getMessages();
        for (var m = 0; m < messages.length; m++) {
          var msg = messages[m];
          var subject = msg.getSubject();

          // Skip already processed
          if (processedSubjects[subject]) continue;
          processedSubjects[subject] = true;

          var attachments = msg.getAttachments();
          for (var a = 0; a < attachments.length; a++) {
            if (!attachments[a].getName().toLowerCase().endsWith('.pdf')) continue;

            var entries = processPdf_(attachments[a], subject);
            if (entries.length > 0) {
              allEntries = allEntries.concat(entries);
            }
          }
        }
      }
    } catch (e) {
      Logger.log('Query error: ' + e.message);
    }
  }

  // Upload to Firestore if we found entries
  if (allEntries.length > 0) {
    uploadToFirestore_(allEntries);
  }

  return {
    entriesFound: allEntries.length,
    entries: allEntries
  };
}

// ============ PDF PROCESSING ============

function processPdf_(attachment, emailSubject) {
  var blob = attachment.copyBlob();
  var fileName = attachment.getName();

  // Convert PDF to Google Doc (Drive OCR)
  var tempFile = Drive.Files.create(
    { title: 'justpass_exam_' + Date.now(), mimeType: MimeType.GOOGLE_DOCS },
    blob,
    { ocr: true }
  );

  try {
    var doc = DocumentApp.openById(tempFile.id);
    var text = doc.getBody().getText();

    if (!text || text.length < 50) return [];

    var examType = detectExamType_(fileName, text);
    var entries = parseExamTimetableText_(text);

    // Add exam type and source to each entry
    for (var i = 0; i < entries.length; i++) {
      entries[i].examType = examType;
      entries[i].sourceFile = fileName;
      entries[i].emailSubject = emailSubject;
      entries[i].timing = SESSION_TIMINGS[entries[i].session] || '';
    }

    return entries;
  } finally {
    try { DriveApp.getFileById(tempFile.id).setTrashed(true); } catch (_) {}
  }
}

function detectExamType_(fileName, text) {
  var combined = (fileName + ' ' + text.substring(0, 500)).toLowerCase();
  if (combined.includes('cat-ii') || combined.includes('cat - ii') || combined.includes('cat 2')) return 'CAT-II';
  if (combined.includes('cat-i') || combined.includes('cat - i') || combined.includes('cat 1')) return 'CAT-I';
  if (combined.includes('end semester') || combined.includes('ese') || combined.includes('university exam')) return 'End Semester';
  if (combined.includes('model exam') || combined.includes('model test')) return 'Model Exam';
  if (combined.includes('retest') || combined.includes('re-test')) return 'Retest';
  return 'Exam';
}

// ============ TIMETABLE PARSER (Multi-line OCR) ============

function parseExamTimetableText_(text) {
  var entries = [];
  var lines = text.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l.length > 0; });

  var courseCodePattern = /^([A-Z]{2,4}\d{3,4})$/;
  var sessionPattern = /^(FN-II|FN-I|AN-II|AN-I|FN|AN)$/i;
  var datePattern = /^(\d{1,2})-(\d{1,2})-(\d{2,4})$/;
  var sessionTimePattern = /^(FN-II|FN-I|AN-II|AN-I|FN|AN)\s*\(/i;
  var branchNames = ['CSE', 'ECE', 'EEE', 'MECH', 'MECHANICAL', 'CIVIL', 'IT', 'CSBS', 'AI&DS', 'AIDS',
    'EIE', 'BME', 'BIOMEDICAL', 'AUTO', 'AUTOMOBILE', 'PROD', 'PRODUCTION', 'CCE', 'ROBOTICS', 'META'];
  var mNames = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

  var currentDate = '';
  var currentDay = '';
  var currentSession = '';

  // Pre-scan: extract first date from header
  var headerDateMatch = text.match(/held from\s*(\d{1,2})-(\d{1,2})-(\d{2,4})/i);
  if (headerDateMatch) {
    var hd = parseInt(headerDateMatch[1]);
    var hm = parseInt(headerDateMatch[2]);
    var hy = parseInt(headerDateMatch[3]);
    if (hy < 100) hy += 2000;
    currentDate = hd + ' ' + (mNames[hm-1] || '?') + ' ' + hy;
  }

  function isBranchLine(line) {
    var upper = line.toUpperCase().replace(/&/g, '&').trim();
    if (branchNames.indexOf(upper) >= 0 || branchNames.indexOf(upper.replace(/ /g,'')) >= 0) return true;
    var parts = upper.split(/\s*,\s*/);
    if (parts.length > 1) {
      var allBranch = true;
      for (var p = 0; p < parts.length; p++) {
        var cleaned = parts[p].trim();
        if (branchNames.indexOf(cleaned) < 0 && branchNames.indexOf(cleaned.replace(/ /g,'')) < 0) {
          allBranch = false;
          break;
        }
      }
      return allBranch;
    }
    return false;
  }

  function isTimeLine(line) {
    if (/^\(?\d{1,2}[.:]+\d{2}\s*(am|pm)/i.test(line)) return true;
    if (/^to$/i.test(line)) return true;
    if (/^\d{1,2}[.:]+\d{2}\s*(am|pm)\)?/i.test(line)) return true;
    return false;
  }

  function isSessionOrSessionTime(line) {
    if (sessionPattern.test(line)) return true;
    if (sessionTimePattern.test(line)) return true;
    return false;
  }

  function extractSession(line) {
    var m = line.match(/^(FN-II|FN-I|AN-II|AN-I|FN|AN)/i);
    return m ? m[1].toUpperCase() : '';
  }

  function isDayLine(line) {
    return /^\(?(Mon(?:day)?|Tue(?:sday)?|Wed(?:nesday)?|Thu(?:rsday)?|Fri(?:day)?|Sat(?:urday)?)\)?$/i.test(line);
  }

  function parseDate(line) {
    var dm = line.match(datePattern);
    if (!dm) return null;
    var d = parseInt(dm[1]);
    var m = parseInt(dm[2]);
    var y = dm[3] ? parseInt(dm[3]) : new Date().getFullYear();
    if (y < 100) y += 2000;
    return d + ' ' + (mNames[m-1] || '?') + ' ' + y;
  }

  var i = 0;
  while (i < lines.length) {
    var line = lines[i];
    var lower = line.toLowerCase();

    if (lower.includes('psg institute') || lower.includes('controller') ||
        lower.includes('circular') || lower.includes('schedule for the test') ||
        lower.includes('semester b.e') || lower.includes('held from') ||
        (lower.includes('date') && lower.includes('session') && lower.includes('course')) ||
        (lower.includes('course') && lower.includes('code') && lower.includes('branch'))) {
      i++; continue;
    }

    var parsedDate = parseDate(line);
    if (parsedDate) { currentDate = parsedDate; i++; continue; }

    if (isSessionOrSessionTime(line)) { currentSession = extractSession(line); i++; continue; }

    if (isDayLine(line)) {
      var dayMatch = line.match(/(Mon|Tue|Wed|Thu|Fri|Sat)/i);
      if (dayMatch) currentDay = dayMatch[1].substring(0, 3);
      i++; continue;
    }

    if (isTimeLine(line)) { i++; continue; }

    if (courseCodePattern.test(line)) {
      var courseCode = line.toUpperCase();
      var courseName = '';
      var branch = '';

      var j = i + 1;
      while (j < lines.length && j <= i + 5) {
        var nextLine = lines[j];
        if (courseCodePattern.test(nextLine)) break;
        if (isSessionOrSessionTime(nextLine)) { currentSession = extractSession(nextLine); j++; continue; }
        var nextDate = parseDate(nextLine);
        if (nextDate) { currentDate = nextDate; j++; continue; }
        if (isTimeLine(nextLine)) { j++; continue; }
        if (isDayLine(nextLine)) {
          var dm2 = nextLine.match(/(Mon|Tue|Wed|Thu|Fri|Sat)/i);
          if (dm2) currentDay = dm2[1].substring(0, 3);
          j++; continue;
        }
        if (isBranchLine(nextLine)) {
          branch = nextLine.toUpperCase().replace(/&/g, '&');
          j++; continue;
        }
        if (!courseName) courseName = nextLine;
        j++;
      }

      if (currentDate || currentSession) {
        entries.push({
          date: currentDate,
          day: currentDay,
          session: currentSession,
          courseCode: courseCode,
          courseName: courseName || courseCode,
          branch: branch || 'All'
        });
      }
      i = j;
      continue;
    }

    i++;
  }

  return entries;
}

// ============ FIRESTORE UPLOAD ============

function uploadToFirestore_(entries) {
  if (!entries || entries.length === 0) return;

  var token = ScriptApp.getOAuthToken();
  var userEmail = Session.getActiveUser().getEmail();

  // Group entries by examType (e.g., "CAT-II")
  var grouped = {};
  for (var i = 0; i < entries.length; i++) {
    var key = entries[i].examType || 'Exam';
    if (!grouped[key]) grouped[key] = [];
    grouped[key].push(entries[i]);
  }

  // Upload each group as a Firestore document
  for (var examType in grouped) {
    var docId = examType.replace(/[^a-zA-Z0-9-]/g, '_') + '_' +
                new Date().getFullYear();

    var docData = {
      fields: {
        examType: { stringValue: examType },
        uploadedBy: { stringValue: userEmail },
        uploadedAt: { timestampValue: new Date().toISOString() },
        entryCount: { integerValue: String(grouped[examType].length) },
        entries: {
          arrayValue: {
            values: grouped[examType].map(function(e) {
              return {
                mapValue: {
                  fields: {
                    date: { stringValue: e.date || '' },
                    day: { stringValue: e.day || '' },
                    session: { stringValue: e.session || '' },
                    timing: { stringValue: e.timing || '' },
                    courseCode: { stringValue: e.courseCode || '' },
                    courseName: { stringValue: e.courseName || '' },
                    branch: { stringValue: e.branch || '' },
                    examType: { stringValue: e.examType || '' }
                  }
                }
              };
            })
          }
        }
      }
    };

    var url = 'https://firestore.googleapis.com/v1/projects/' + FIRESTORE_PROJECT +
              '/databases/(default)/documents/' + FIRESTORE_COLLECTION + '/' + docId;

    try {
      UrlFetchApp.fetch(url, {
        method: 'PATCH',
        headers: {
          'Authorization': 'Bearer ' + token,
          'Content-Type': 'application/json'
        },
        payload: JSON.stringify(docData),
        muteHttpExceptions: true
      });
      Logger.log('Uploaded ' + grouped[examType].length + ' entries for ' + examType);
    } catch (e) {
      Logger.log('Firestore upload error: ' + e.message);
    }
  }
}

// ============ BACKGROUND TRIGGER ============

function ensureTrigger_() {
  var triggers = ScriptApp.getProjectTriggers();
  for (var i = 0; i < triggers.length; i++) {
    if (triggers[i].getHandlerFunction() === 'backgroundSync') return;
  }

  // Create a trigger that runs every 15 minutes
  ScriptApp.newTrigger('backgroundSync')
    .timeBased()
    .everyMinutes(15)
    .create();
  Logger.log('Background trigger created');
}

function backgroundSync() {
  scanAndSync_();
}
