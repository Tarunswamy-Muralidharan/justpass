# JustPass Apps Script Setup

## What This Does
Automatically scans your Gmail every 10 minutes for college exam seat Excel files,
parses them, and serves the results via a web API that the JustPass app can query.

## One-Time Setup (5 minutes)

### Step 1: Create the Script
1. Go to [script.google.com](https://script.google.com)
2. Click **New Project**
3. Delete the default code and paste the contents of `Code.gs`
4. Name the project: `JustPass Exam Parser`

### Step 2: Enable Drive API
1. In the script editor, click **Services** (+ icon on the left panel)
2. Find **Drive API** and click **Add**
3. Click **OK**

### Step 3: Deploy as Web App
1. Click **Deploy** → **New deployment**
2. Click the gear icon → Select **Web app**
3. Settings:
   - Description: `JustPass Exam Seat API`
   - Execute as: **Me**
   - Who has access: **Anyone**
4. Click **Deploy**
5. **Copy the Web App URL** — this is your API endpoint
   (looks like: `https://script.google.com/macros/s/AKfyc.../exec`)

### Step 4: Start Auto-Scanning
1. In the script editor, select `setupTrigger` from the function dropdown
2. Click **Run**
3. Grant permissions when prompted (Gmail + Drive access)
4. Done! The script will now scan every 10 minutes

## API Endpoints

All requests are GET to your Web App URL:

### Look up exam seat
```
GET {URL}?action=lookup&roll=23Z201
```
Response:
```json
{
  "found": true,
  "rollNumber": "23Z201",
  "seats": [
    {
      "hall": "A101",
      "seatNumber": "15",
      "examName": "Session: FN-I",
      "date": "3 Apr 2026",
      "timing": "8:30 AM - 10:15 AM",
      "sourceFile": "3 Yr 04-03 FN-I.xlsx"
    }
  ]
}
```

### Get latest processed info
```
GET {URL}?action=latest
```

### Get stats
```
GET {URL}?action=stats
```

## How It Works
1. Time trigger fires every 10 minutes
2. Script searches Gmail: `from:(@psgtech.ac.in OR @psgitech.ac.in) has:attachment filename:xls`
3. For each new Excel attachment:
   - Converts to Google Sheets (temporary)
   - Finds hall/seat columns by header matching
   - Extracts roll number, hall, seat for every row
   - Stores in a Google Sheet ("JustPass Exam Seats DB")
   - Deletes the temporary sheet
4. Marks the email with "JustPass/Processed" label (won't re-process)
5. Android app queries the web API with the student's roll number

## Deployment Models

### Option A: Centralized (Recommended)
- **YOU** (developer) run the script on your Google account
- Students set up a Gmail filter to forward college emails to your Gmail
- OR you manually forward them
- One script serves all 1.4k users

### Option B: Per-User
- Each student deploys their own copy
- Script runs in their own Gmail
- More private, but requires each student to do the setup

## Troubleshooting

- **No emails found?** Check the search query matches your college's email domain
- **Permission denied?** Re-run `setupTrigger()` and grant all permissions
- **Trigger stopped?** Check Triggers tab (clock icon) in script editor
- **Wrong data?** Check the "JustPass Exam Seats DB" spreadsheet in your Drive
