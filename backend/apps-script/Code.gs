/**
 * JustPass Exam Auto-Parser
 * Google Apps Script that monitors Gmail for college exam emails:
 *   - Excel attachments -> Exam seat arrangements (hall + seat per roll number)
 *   - PDF attachments  -> Exam timetables (date + session + subject per branch)
 *
 * SETUP:
 * 1. Open script.google.com -> New Project
 * 2. Paste this code
 * 3. Enable Drive API: Services -> + -> Drive API
 * 4. Deploy -> Web app -> Execute as "Me", Access "Anyone"
 * 5. Run setupTrigger() once to start auto-scanning
 */

// ============ CONFIGURATION ============

const CONFIG = {
  // Gmail search - picks up both Excel and PDF from examcell/college
  SEARCH_QUERY: '(from:(@psgtech.ac.in OR @psgitech.ac.in) OR subject:(CAT exam seating timetable examcell)) has:attachment',
  PROCESSED_LABEL: 'JustPass/Processed',
  MAX_EMAILS_PER_RUN: 10,
  SPREADSHEET_NAME: 'JustPass Exam Data',
};

const HALL_HEADERS = ['hall', 'room', 'venue', 'hall no', 'hall name', 'room no'];
const SEAT_HEADERS = ['seat', 'seat no', 'seat number', 'seat no.', 's.no', 'bench'];
const SESSION_TIMINGS = {
  'FN-I': '8:45 AM - 10:30 AM',
  'FN-II': '10:45 AM - 12:30 PM',
  'AN-I': '12:45 PM - 2:30 PM',
  'AN-II': '2:45 PM - 4:30 PM',
  'FN': '8:45 AM - 12:30 PM',
  'AN': '12:45 PM - 4:30 PM',
};

const BRANCH_ALIASES = {
  'CSE': ['cse', 'computer science', 'comp sci'],
  'ECE': ['ece', 'electronics and communication', 'electronics & communication'],
  'EEE': ['eee', 'electrical and electronics', 'electrical & electronics'],
  'MECH': ['mech', 'mechanical'],
  'CIVIL': ['civil'],
  'IT': ['it', 'information technology'],
  'CSBS': ['csbs', 'computer science and business', 'cs & business'],
  'AIDS': ['aids', 'ai & ds', 'ai and data science', 'artificial intelligence'],
  'EIE': ['eie', 'electronics and instrumentation', 'electronics & instrumentation'],
  'BME': ['bme', 'biomedical'],
  'AUTO': ['auto', 'automobile'],
  'PROD': ['prod', 'production'],
  'ROBOT': ['robot', 'robotics'],
  'META': ['meta', 'metallurgy'],
  'CCE': ['cce', 'computer and communication'],
};

// ============ TRIGGER SETUP ============

function setupTrigger() {
  // TEMP: clear timetable and reprocess with updated parser
  var ss = getOrCreateSpreadsheet_();
  var tt = ss.getSheetByName('ExamTimetable');
  if (tt && tt.getLastRow() > 1) { tt.deleteRows(2, tt.getLastRow() - 1); Logger.log('Cleared ExamTimetable rows'); }
  var label = GmailApp.getUserLabelByName(CONFIG.PROCESSED_LABEL);
  if (label) { label.getThreads().forEach(function(t) { t.removeLabel(label); }); Logger.log('Removed processed labels'); }
  processNewEmails();
  ScriptApp.getProjectTriggers().forEach(t => ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('processNewEmails').timeBased().everyMinutes(10).create();
  Logger.log('Done reprocessing + trigger reset');
}

function removeTrigger() {
  ScriptApp.getProjectTriggers().forEach(t => ScriptApp.deleteTrigger(t));
  Logger.log('All triggers removed.');
}

// ============ EMAIL PROCESSING ============

function processNewEmails() {
  const query = CONFIG.SEARCH_QUERY + ' -label:' + CONFIG.PROCESSED_LABEL.replace('/', '-');
  const threads = GmailApp.search(query, 0, CONFIG.MAX_EMAILS_PER_RUN);

  if (threads.length === 0) {
    Logger.log('No new emails found.');
    return;
  }

  Logger.log('Found ' + threads.length + ' thread(s) to process.');

  let label = GmailApp.getUserLabelByName(CONFIG.PROCESSED_LABEL);
  if (!label) label = GmailApp.createLabel(CONFIG.PROCESSED_LABEL);

  const spreadsheet = getOrCreateSpreadsheet_();

  for (const thread of threads) {
    try {
      const messages = thread.getMessages();
      let processed = false;
      for (const message of messages) {
        const attachments = message.getAttachments();
        for (const attachment of attachments) {
          const name = attachment.getName().toLowerCase();
          if (name.endsWith('.xls') || name.endsWith('.xlsx')) {
            Logger.log('Excel: ' + attachment.getName());
            processExcelAttachment_(attachment, message, spreadsheet);
            processed = true;
          } else if (name.endsWith('.pdf')) {
            Logger.log('PDF: ' + attachment.getName());
            processPdfAttachment_(attachment, message, spreadsheet);
            processed = true;
          }
        }
      }
      if (processed) thread.addLabel(label);
    } catch (e) {
      Logger.log('Error: ' + e.message);
    }
  }
  Logger.log('Done.');
}
// ============ EXCEL PROCESSING (Seat Arrangements) ============

function processExcelAttachment_(attachment, message, spreadsheet) {
  const blob = attachment.copyBlob();
  const fileName = attachment.getName();

  const tempFile = Drive.Files.create(
    { title: 'justpass_temp_' + Date.now(), mimeType: MimeType.GOOGLE_SHEETS },
    blob
  );

  try {
    const tempSpreadsheet = SpreadsheetApp.openById(tempFile.id);
    const sheets = tempSpreadsheet.getSheets();
    const examInfo = parseExamInfoFromFilename_(fileName);
    const emailDate = message.getDate();
    const emailSubject = message.getSubject();
    const resultsSheet = getOrCreateSheet_(spreadsheet, 'ExamSeats', [
      'Roll Number', 'Hall', 'Seat', 'Exam Session', 'Date',
      'Timing', 'Source File', 'Email Subject', 'Email Date', 'Processed At'
    ]);

    let rowsAdded = 0;
    for (const sheet of sheets) {
      const data = sheet.getDataRange().getValues();
      if (data.length < 2) continue;
      const headers = findHeaderColumns_(data);
      if (headers.headerRow < 0) continue;

      for (let r = headers.headerRow + 1; r < data.length; r++) {
        const row = data[r];
        const rollInfo = findRollNumberInRow_(row);
        if (!rollInfo) continue;
        const hall = headers.hallCol >= 0 ? String(row[headers.hallCol]).trim() : '';
        const seat = headers.seatCol >= 0 ? String(row[headers.seatCol]).trim() : '';
        if (!hall && !seat) continue;
        const rollNorm = normalizeRollNumber_(rollInfo);
        if (checkDuplicate_(resultsSheet, 0, rollNorm, 3, examInfo.session, 4, examInfo.date)) continue;

        resultsSheet.appendRow([
          rollNorm, hall || 'N/A', seat || 'N/A', examInfo.session, examInfo.date,
          examInfo.timing, fileName, emailSubject,
          Utilities.formatDate(emailDate, Session.getScriptTimeZone(), 'yyyy-MM-dd HH:mm'),
          new Date().toISOString()
        ]);
        rowsAdded++;
      }
    }
    Logger.log('Added ' + rowsAdded + ' seat records from ' + fileName);
  } finally {
    try { DriveApp.getFileById(tempFile.id).setTrashed(true); } catch (_) {}
  }
}

// ============ PDF PROCESSING (Exam Timetables) ============

function processPdfAttachment_(attachment, message, spreadsheet) {
  const blob = attachment.copyBlob();
  const fileName = attachment.getName();

  const tempFile = Drive.Files.create(
    { title: 'justpass_pdf_' + Date.now(), mimeType: MimeType.GOOGLE_DOCS },
    blob,
    { ocr: true }
  );

  try {
    const doc = DocumentApp.openById(tempFile.id);
    const text = doc.getBody().getText();
    Logger.log('PDF text length: ' + text.length);
    Logger.log('PDF text preview: ' + text.substring(0, 2000));

    if (!text || text.length < 50) {
      Logger.log('PDF text too short, skipping: ' + fileName);
      return;
    }

    const examType = detectExamType_(fileName, text);
    const emailDate = message.getDate();
    const emailSubject = message.getSubject();

    const entries = parseExamTimetableText_(text);
    if (entries.length === 0) {
      Logger.log('No timetable entries found in: ' + fileName);
      return;
    }

    const timetableSheet = getOrCreateSheet_(spreadsheet, 'ExamTimetable', [
      'Date', 'Day', 'Session', 'Timing', 'Course Code', 'Course Name',
      'Branch', 'Exam Type', 'Source File', 'Email Subject', 'Processed At'
    ]);

    let rowsAdded = 0;
    for (const entry of entries) {
      if (checkDuplicate_(timetableSheet, 0, entry.date, 2, entry.session, 4, entry.courseCode)) continue;

      timetableSheet.appendRow([
        entry.date,
        entry.day,
        entry.session,
        SESSION_TIMINGS[entry.session] || '',
        entry.courseCode,
        entry.courseName,
        entry.branch,
        examType,
        fileName,
        emailSubject,
        new Date().toISOString()
      ]);
      rowsAdded++;
    }

    Logger.log('Added ' + rowsAdded + ' timetable entries from ' + fileName);
  } finally {
    try { DriveApp.getFileById(tempFile.id).setTrashed(true); } catch (_) {}
  }
}

function detectExamType_(fileName, text) {
  const combined = (fileName + ' ' + text.substring(0, 500)).toLowerCase();
  if (combined.includes('cat-ii') || combined.includes('cat - ii') || combined.includes('cat 2')) return 'CAT-II';
  if (combined.includes('cat-i') || combined.includes('cat - i') || combined.includes('cat 1')) return 'CAT-I';
  if (combined.includes('end semester') || combined.includes('ese') || combined.includes('university exam')) return 'End Semester';
  if (combined.includes('model exam') || combined.includes('model test')) return 'Model Exam';
  if (combined.includes('retest') || combined.includes('re-test')) return 'Retest';
  return 'Exam';
}
function parseExamTimetableText_(text) {
  var entries = [];
  var lines = text.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l.length > 0; });

  var courseCodeRegex = /\b([A-Z]{2,4}\d{3,4})\b/;
  var courseCodeStrictRegex = /^([A-Z]{2,4}\d{3,4})$/;
  var sessionRegex = /\b(FN-II|FN-I|AN-II|AN-I|FN|AN)\b/i;
  var dateRegex = /\b(\d{1,2})-(\d{1,2})-(\d{2,4})\b/;
  var branchNames = ['CSE', 'ECE', 'EEE', 'MECH', 'MECHANICAL', 'CIVIL', 'IT', 'CSBS', 'AI&DS', 'AIDS',
    'EIE', 'BME', 'BIOMEDICAL', 'AUTO', 'AUTOMOBILE', 'PROD', 'PRODUCTION', 'CCE', 'ROBOTICS', 'META'];
  var mNames = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  var branchSet = {};
  branchNames.forEach(function(b) { branchSet[b] = true; });

  var currentDate = '';
  var currentDay = '';
  var currentSession = '';

  // Pre-scan: extract first date from header text
  var headerDateMatch = text.match(/held from\s*(\d{1,2})-(\d{1,2})-(\d{2,4})/i);
  if (headerDateMatch) {
    var hd = parseInt(headerDateMatch[1]);
    var hm = parseInt(headerDateMatch[2]);
    var hy = parseInt(headerDateMatch[3]);
    if (hy < 100) hy += 2000;
    currentDate = hd + ' ' + (mNames[hm-1] || '?') + ' ' + hy;
  }

  function formatDate(d, m, y) {
    if (y < 100) y += 2000;
    return d + ' ' + (mNames[m-1] || '?') + ' ' + y;
  }

  function extractBranch(text) {
    var upper = text.toUpperCase().replace(/&/g, '&').trim();
    // Check comma-separated branches first
    var parts = upper.split(/\s*,\s*/);
    if (parts.length >= 1) {
      var matched = [];
      for (var p = 0; p < parts.length; p++) {
        var cleaned = parts[p].trim().replace(/ /g, '');
        if (branchSet[cleaned] || branchSet[parts[p].trim()]) {
          matched.push(parts[p].trim());
        }
      }
      if (matched.length > 0) return matched.join(', ');
    }
    return '';
  }

  function isDayWord(word) {
    return /^(Mon(?:day)?|Tue(?:sday)?|Wed(?:nesday)?|Thu(?:rsday)?|Fri(?:day)?|Sat(?:urday)?)$/i.test(word.replace(/[()]/g, ''));
  }

  function isNoiseLine(line) {
    var lower = line.toLowerCase();
    return lower.includes('psg institute') || lower.includes('controller') ||
        lower.includes('circular') || lower.includes('schedule for the test') ||
        lower.includes('semester b.e') || lower.includes('held from') ||
        lower.includes('course name') || lower.includes('course code') ||
        lower.includes('ref:') || lower.includes('office of') ||
        (lower.includes('date') && lower.includes('session')) ||
        (lower.includes('date') && lower.includes('course')) ||
        (lower.startsWith('date') && lower.includes(':'));
  }

  function isTimePart(s) {
    return /^\(?\d{1,2}[.:]\d{2}\s*(am|pm)?/i.test(s) || s === 'to' || /^\d{1,2}[.:]\d{2}\s*(am|pm)\)?$/i.test(s);
  }

  // ─── Main parse loop ───
  var i = 0;
  while (i < lines.length) {
    var line = lines[i];

    if (isNoiseLine(line)) { i++; continue; }

    // Extract date from anywhere in the line — but skip "Date: 6-4-2026" header lines
    var dateMatch = line.match(dateRegex);
    if (dateMatch && !line.toLowerCase().startsWith('date')) {
      currentDate = formatDate(parseInt(dateMatch[1]), parseInt(dateMatch[2]), parseInt(dateMatch[3]));
    }

    // Extract day from anywhere in the line
    var dayMatch = line.match(/\(?(Mon(?:day)?|Tue(?:sday)?|Wed(?:nesday)?|Thu(?:rsday)?|Fri(?:day)?|Sat(?:urday)?)\)?/i);
    if (dayMatch) {
      currentDay = dayMatch[1].substring(0, 3);
    }

    // Extract session from anywhere in the line (order matters: FN-II before FN-I)
    var sessMatch = line.match(/\b(FN-II|FN-I|AN-II|AN-I)\b/i);
    if (sessMatch) {
      currentSession = sessMatch[1].toUpperCase();
    }

    // Extract course code from anywhere in the line
    var codeMatch = line.match(courseCodeRegex);
    if (codeMatch) {
      var courseCode = codeMatch[1].toUpperCase();
      var courseName = '';
      var branch = '';

      // Try to extract name and branch from the SAME line (after the code)
      var afterCode = line.substring(line.indexOf(codeMatch[0]) + codeMatch[0].length).trim();
      if (afterCode.length > 0) {
        // Check if the remainder contains a branch at the end
        var branchFromLine = extractBranch(afterCode);
        if (branchFromLine) {
          // Remove branch from afterCode to get course name
          var branchIdx = afterCode.toUpperCase().lastIndexOf(branchFromLine.split(',')[0].trim());
          if (branchIdx > 0) {
            courseName = afterCode.substring(0, branchIdx).trim().replace(/,\s*$/, '');
          }
          branch = branchFromLine;
        } else {
          // The rest is the course name
          courseName = afterCode;
        }
      }

      // If no name yet, look ahead for course name and branch
      if (!courseName || !branch) {
        var j = i + 1;
        while (j < lines.length && j <= i + 6) {
          var nextLine = lines[j];
          if (isNoiseLine(nextLine)) { j++; continue; }

          // If next line has a course code, try to extract name before it
          if (courseCodeRegex.test(nextLine)) {
            // Check if line has text BEFORE the code (e.g., "Engineering Geology AG3601")
            var codePos = nextLine.search(courseCodeRegex);
            if (codePos > 3 && !courseName) {
              var beforeCode = nextLine.substring(0, codePos).trim();
              if (beforeCode.length > 2 && !extractBranch(beforeCode)) {
                courseName = beforeCode;
              }
            }
            break;
          }
          // Stop if next line is a new date (but not if it also contains useful text)
          if (dateRegex.test(nextLine) && nextLine.length < 15) break;

          // Skip time parts
          if (isTimePart(nextLine)) { j++; continue; }
          // Skip day words as standalone lines
          if (/^\(?(Mon|Tue|Wed|Thu|Fri|Sat)/i.test(nextLine) && nextLine.length < 15) { j++; continue; }
          // Skip standalone semester numbers
          if (/^\d{1,2}$/.test(nextLine)) { j++; continue; }
          // Skip "PASS" / "RESULT" lines
          if (nextLine.toUpperCase() === 'PASS' || nextLine.toUpperCase() === 'RESULT') { j++; continue; }

          // Check if it's a session marker
          var nextSess = nextLine.match(/^(FN-II|FN-I|AN-II|AN-I)\b/i);
          if (nextSess) { j++; continue; }

          // Check if it's a branch
          var br = extractBranch(nextLine);
          if (br && !branch) { branch = br; j++; continue; }

          // Otherwise it's the course name (must be > 2 chars, not just numbers)
          if (!courseName && nextLine.length > 2 && !/^\d+$/.test(nextLine)) {
            courseName = nextLine;
          }
          j++;
        }
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
    }

    i++;
  }

  return entries;
}

// ============ SHARED HELPERS ============

function findHeaderColumns_(data) {
  let hallCol = -1, seatCol = -1, headerRow = -1;
  for (let r = 0; r < Math.min(10, data.length); r++) {
    for (let c = 0; c < data[r].length; c++) {
      const val = String(data[r][c]).toLowerCase().trim();
      if (!val) continue;
      if (HALL_HEADERS.some(h => val.includes(h))) { hallCol = c; headerRow = r; }
      if (SEAT_HEADERS.some(h => val.includes(h))) { seatCol = c; headerRow = r; }
    }
    if (hallCol >= 0 || seatCol >= 0) break;
  }
  return { hallCol, seatCol, headerRow };
}

function findRollNumberInRow_(row) {
  const rollPattern = /^\d{2}[A-Z]\d{3}$/i;
  const extendedPattern = /\b(\d{2}[A-Z]\d{3})\b/i;
  for (let c = 0; c < row.length; c++) {
    const val = String(row[c]).trim().replace(/[\s.\-\u00A0\u200B]+/g, '').toUpperCase();
    if (!val) continue;
    if (rollPattern.test(val)) return val;
    const match = val.match(extendedPattern);
    if (match) return match[1];
  }
  return null;
}

function normalizeRollNumber_(raw) {
  return String(raw).trim().replace(/[\s.\-\u00A0\u200B]+/g, '').toUpperCase();
}

function parseExamInfoFromFilename_(fileName) {
  const name = fileName.replace(/\.[^.]+$/, '');
  const sessionMatch = name.match(/(FN-II|FN-I|AN-II|AN-I|FN|AN)/i);
  const sessionCode = sessionMatch ? sessionMatch[1].toUpperCase() : '';
  const timing = SESSION_TIMINGS[sessionCode] || '';
  const dateMatch = name.match(/(\d{2})-(\d{2})/);
  let dateStr = '';
  if (dateMatch) {
    const part1 = parseInt(dateMatch[1]);
    const part2 = parseInt(dateMatch[2]);
    let month, day;
    if (part1 > 12) { day = part1; month = part2; }
    else if (part2 > 12) { day = part2; month = part1; }
    else { month = part1; day = part2; }
    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                         'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    dateStr = day + ' ' + (monthNames[month - 1] || '?') + ' ' + new Date().getFullYear();
  }
  return { session: sessionCode ? 'Session: ' + sessionCode : '', date: dateStr, timing: timing };
}

function checkDuplicate_(sheet, col1, val1, col2, val2, col3, val3) {
  const data = sheet.getDataRange().getValues();
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][col1]).toUpperCase() === String(val1).toUpperCase() &&
        String(data[r][col2]) === String(val2) &&
        String(data[r][col3]) === String(val3)) {
      return true;
    }
  }
  return false;
}
// ============ SPREADSHEET MANAGEMENT ============

function getOrCreateSpreadsheet_() {
  const files = DriveApp.getFilesByName(CONFIG.SPREADSHEET_NAME);
  if (files.hasNext()) return SpreadsheetApp.open(files.next());
  const ss = SpreadsheetApp.create(CONFIG.SPREADSHEET_NAME);
  Logger.log('Created spreadsheet: ' + ss.getUrl());
  return ss;
}

function getOrCreateSheet_(spreadsheet, name, headers) {
  let sheet = spreadsheet.getSheetByName(name);
  if (!sheet) {
    sheet = spreadsheet.insertSheet(name);
    sheet.appendRow(headers);
    sheet.getRange(1, 1, 1, headers.length).setFontWeight('bold');
    sheet.setFrozenRows(1);
  }
  return sheet;
}

// ============ WEB APP API ============

function doGet(e) {
  const action = (e.parameter.action || 'lookup').toLowerCase();
  const roll = normalizeRollNumber_(e.parameter.roll || '');
  const branch = (e.parameter.branch || '').toUpperCase();

  try {
    switch (action) {
      case 'lookup':
        return jsonResponse_(lookupSeat_(roll));
      case 'timetable':
        return jsonResponse_(getExamTimetable_(branch));
      case 'latest':
        return jsonResponse_(getLatestInfo_());
      case 'stats':
        return jsonResponse_(getStats_());
      default:
        return jsonResponse_({ error: 'Unknown action: ' + action });
    }
  } catch (err) {
    return jsonResponse_({ error: err.message });
  }
}

function lookupSeat_(rollNumber) {
  if (!rollNumber) return { found: false, error: 'No roll number provided' };
  const spreadsheet = getOrCreateSpreadsheet_();
  const sheet = spreadsheet.getSheetByName('ExamSeats');
  if (!sheet || sheet.getLastRow() < 2) return { found: false, seats: [], message: 'No data yet' };

  const data = sheet.getDataRange().getValues();
  const seats = [];
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]).toUpperCase() === rollNumber) {
      seats.push({
        hall: String(data[r][1]),
        seatNumber: String(data[r][2]),
        examName: String(data[r][3]),
        date: String(data[r][4]),
        timing: String(data[r][5]),
        sourceFile: String(data[r][6]),
        processedAt: String(data[r][9])
      });
    }
  }
  return { found: seats.length > 0, rollNumber: rollNumber, seats: seats };
}

function getExamTimetable_(branch) {
  const spreadsheet = getOrCreateSpreadsheet_();
  const sheet = spreadsheet.getSheetByName('ExamTimetable');
  if (!sheet || sheet.getLastRow() < 2) return { found: false, entries: [], message: 'No timetable data yet' };

  const data = sheet.getDataRange().getValues();
  const entries = [];
  for (let r = 1; r < data.length; r++) {
    const rowBranch = String(data[r][6]).toUpperCase();
    if (!branch || rowBranch === 'ALL' || rowBranch.includes(branch)) {
      entries.push({
        date: String(data[r][0]),
        day: String(data[r][1]),
        session: String(data[r][2]),
        timing: String(data[r][3]),
        courseCode: String(data[r][4]),
        courseName: String(data[r][5]),
        branch: String(data[r][6]),
        examType: String(data[r][7])
      });
    }
  }
  return { found: entries.length > 0, branch: branch || 'All', entries: entries };
}

function getLatestInfo_() {
  const spreadsheet = getOrCreateSpreadsheet_();
  const seatSheet = spreadsheet.getSheetByName('ExamSeats');
  const ttSheet = spreadsheet.getSheetByName('ExamTimetable');
  return {
    seatRecords: seatSheet ? Math.max(0, seatSheet.getLastRow() - 1) : 0,
    timetableRecords: ttSheet ? Math.max(0, ttSheet.getLastRow() - 1) : 0,
  };
}

function getStats_() {
  const spreadsheet = getOrCreateSpreadsheet_();
  const seatSheet = spreadsheet.getSheetByName('ExamSeats');
  const ttSheet = spreadsheet.getSheetByName('ExamTimetable');
  return {
    seatRecords: seatSheet ? Math.max(0, seatSheet.getLastRow() - 1) : 0,
    timetableRecords: ttSheet ? Math.max(0, ttSheet.getLastRow() - 1) : 0,
    triggerActive: ScriptApp.getProjectTriggers().length > 0
  };
}

function jsonResponse_(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

// ============ TEST FUNCTIONS ============

function testCatPdf() {
  // Find the CAT-II email specifically
  var threads = GmailApp.search('subject:"Continuous Assessment Test II" has:attachment newer_than:7d', 0, 1);
  if (threads.length === 0) {
    // Try broader search
    threads = GmailApp.search('subject:CAT has:attachment newer_than:7d', 0, 1);
  }
  if (threads.length === 0) {
    Logger.log('No CAT email found!');
    return;
  }
  var msgs = threads[0].getMessages();
  for (var i = 0; i < msgs.length; i++) {
    var atts = msgs[i].getAttachments();
    Logger.log('Message ' + i + ': ' + msgs[i].getSubject() + ' | ' + atts.length + ' attachments');
    for (var j = 0; j < atts.length; j++) {
      var att = atts[j];
      Logger.log('Attachment: ' + att.getName() + ' (' + att.getContentType() + ')');
      if (att.getName().toLowerCase().endsWith('.pdf')) {
        var blob = att.copyBlob();
        var tempFile = Drive.Files.create(
          { title: 'justpass_debug_' + Date.now(), mimeType: MimeType.GOOGLE_DOCS },
          blob,
          { ocr: true }
        );
        var doc = DocumentApp.openById(tempFile.id);
        var text = doc.getBody().getText();
        Logger.log('=== FULL OCR TEXT (' + text.length + ' chars) ===');
        Logger.log(text);
        Logger.log('=== END OCR TEXT ===');
        DriveApp.getFileById(tempFile.id).setTrashed(true);
      }
    }
  }
}

function testProcessNow() { processNewEmails(); }
function testLookup() { Logger.log(JSON.stringify(lookupSeat_('23Z201'), null, 2)); }
function testTimetable() { Logger.log(JSON.stringify(getExamTimetable_('CSE'), null, 2)); }

function debugSearchEmail() {
  // Search for the forwarded CAT email
  var queries = [
    'subject:CAT newer_than:1d has:attachment',
    'from:psgitech newer_than:7d has:attachment',
    'CAT timetable newer_than:1d',
    'Fwd: newer_than:1d has:attachment'
  ];
  for (var i = 0; i < queries.length; i++) {
    var threads = GmailApp.search(queries[i], 0, 3);
    Logger.log('Query: "' + queries[i] + '" -> ' + threads.length + ' threads');
    for (var j = 0; j < threads.length; j++) {
      var msgs = threads[j].getMessages();
      for (var k = 0; k < msgs.length; k++) {
        var m = msgs[k];
        var atts = m.getAttachments();
        var attNames = [];
        for (var a = 0; a < atts.length; a++) attNames.push(atts[a].getName());
        Logger.log('  From: ' + m.getFrom() + ' | Subject: ' + m.getSubject() + ' | Attachments: ' + attNames.join(', '));
      }
    }
  }
}

function reprocessAll() {
  // Remove the processed label from all threads so they get re-scanned
  var label = GmailApp.getUserLabelByName(CONFIG.PROCESSED_LABEL);
  if (label) {
    var threads = label.getThreads();
    Logger.log('Removing label from ' + threads.length + ' threads');
    for (var i = 0; i < threads.length; i++) {
      threads[i].removeLabel(label);
    }
  }
  processNewEmails();
}


function testParserOnly() {
  // Find the CAT-II email
  var threads = GmailApp.search('subject:"Continuous Assessment Test II" has:attachment newer_than:7d', 0, 1);
  if (threads.length === 0) { Logger.log('No CAT email found'); return; }
  var msgs = threads[0].getMessages();
  for (var i = 0; i < msgs.length; i++) {
    var atts = msgs[i].getAttachments();
    for (var j = 0; j < atts.length; j++) {
      if (!atts[j].getName().toLowerCase().endsWith('.pdf')) continue;
      var blob = atts[j].copyBlob();
      var tempFile = Drive.Files.create(
        { title: 'justpass_test_' + Date.now(), mimeType: MimeType.GOOGLE_DOCS },
        blob, { ocr: true }
      );
      var doc = DocumentApp.openById(tempFile.id);
      var text = doc.getBody().getText();
      DriveApp.getFileById(tempFile.id).setTrashed(true);
      
      var entries = parseExamTimetableText_(text);
      Logger.log('=== PARSED ' + entries.length + ' ENTRIES ===');
      for (var k = 0; k < entries.length; k++) {
        var e = entries[k];
        Logger.log(k + ': ' + e.date + ' | ' + e.session + ' | ' + e.courseCode + ' | ' + e.courseName + ' | ' + e.branch);
      }
      Logger.log('=== END ENTRIES ===');
    }
  }
}