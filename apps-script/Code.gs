/**
 * Backend pencatat notifikasi bank/e-wallet ke Google Sheets.
 *
 * Cara pakai:
 * 1. Buat Google Sheet baru, buka Extensions > Apps Script, tempel kode ini.
 * 2. Jalankan fungsi `setup` sekali (Run > setup) untuk membuat header &
 *    menyimpan secret key. Ganti nilai MY_SECRET di bawah dulu sebelum run.
 * 3. Deploy > New deployment > pilih tipe "Web app".
 *      - Execute as: Me
 *      - Who has access: Anyone
 * 4. Salin "Web app URL" hasil deploy, tempel ke aplikasi Android (kolom URL Web App).
 * 5. Isi secret key yang SAMA di aplikasi Android.
 */

var SHEET_NAME = 'Transaksi';
var MY_SECRET = 'GANTI_DENGAN_KUNCI_RAHASIA_ANDA';

function setup() {
  PropertiesService.getScriptProperties().setProperty('SECRET', MY_SECRET);

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
  }
  if (sheet.getLastRow() === 0) {
    sheet.appendRow([
      'Waktu Diterima', 'Waktu Notifikasi', 'Aplikasi', 'Paket Aplikasi',
      'Judul', 'Isi Notifikasi', 'Nominal', 'Jenis'
    ]);
    sheet.setFrozenRows(1);
  }
}

function doPost(e) {
  var result = { status: 'error', message: 'unknown error' };

  try {
    var body = JSON.parse(e.postData.contents);
    var expectedSecret = PropertiesService.getScriptProperties().getProperty('SECRET');

    if (!expectedSecret) {
      result.message = 'Server belum di-setup. Jalankan fungsi setup() dulu.';
      return jsonResponse(result);
    }

    if (body.secret !== expectedSecret) {
      result.message = 'Secret key tidak cocok.';
      return jsonResponse(result);
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName(SHEET_NAME);
    if (!sheet) {
      result.message = 'Sheet "' + SHEET_NAME + '" tidak ditemukan. Jalankan setup() dulu.';
      return jsonResponse(result);
    }

    sheet.appendRow([
      new Date(),
      body.timestamp || '',
      body.appName || '',
      body.appPackage || '',
      body.title || '',
      body.text || '',
      body.amount || '',
      body.type || ''
    ]);

    result.status = 'ok';
    result.message = 'Tercatat';
  } catch (err) {
    result.message = err.toString();
  }

  return jsonResponse(result);
}

function jsonResponse(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
