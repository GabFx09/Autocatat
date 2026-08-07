/**
 * Backend pencatat notifikasi bank/e-wallet ke Google Sheets, dengan panel
 * pengaturan supaya tiap aplikasi (BCA, BRI, Mandiri, BNI, DANA, OVO,
 * LinkAja, GoPay) bisa dihubungkan ke Google Sheet & nama tab-nya
 * masing-masing -- dan bisa diganti kapan saja tanpa mengubah apa pun di
 * aplikasi Android.
 *
 * Struktur: BCA -> Sheet terhubung -> Tab terhubung, BRI -> Sheet terhubung
 * -> Tab terhubung, dst -- semuanya diatur lewat panel ini.
 *
 * Cara pakai:
 * 1. Buka https://script.google.com, buat project baru (atau lewat
 *    Extensions > Apps Script dari sebuah Google Sheet), tempel kode ini.
 * 2. Deploy > New deployment > pilih tipe "Web app".
 *      - Execute as: Me
 *      - Who has access: Anyone
 * 3. Salin "Web app URL" hasil deploy. URL ini ditanam langsung di source
 *    code aplikasi Android (lihat Config.kt) saat di-build -- pengguna app
 *    tidak pernah perlu mengisi URL apa pun.
 * 4. Buka URL yang sama itu di browser (bukan dari app) -- akan muncul
 *    Panel Pengaturan dengan 8 bagian (satu per bank/e-wallet). Isi link
 *    Google Sheet & nama tab untuk masing-masing, klik Simpan Semua.
 *
 * Setiap kali kamu mau pindah/ganti Sheet tujuan salah satu bank/e-wallet,
 * cukup buka lagi panel ini dan ubah linknya -- semua HP yang sudah
 * terpasang otomatis ikut tanpa perlu disetel ulang.
 */

var CATEGORIES = [
  { key: 'BCA', label: 'BCA' },
  { key: 'BRI', label: 'BRI' },
  { key: 'MANDIRI', label: 'Mandiri' },
  { key: 'BNI', label: 'BNI' },
  { key: 'DANA', label: 'DANA' },
  { key: 'OVO', label: 'OVO' },
  { key: 'LINKAJA', label: 'LinkAja' },
  { key: 'GOPAY', label: 'GoPay' }
];

function doGet(e) {
  var props = PropertiesService.getScriptProperties();

  if (e.parameter.format === 'json') {
    return jsonResponse_(buildStatus_(props));
  }

  var hasAnyParam = CATEGORIES.some(function (c) {
    return e.parameter['url_' + c.key] !== undefined;
  });

  if (hasAnyParam) {
    CATEGORIES.forEach(function (c) {
      var url = (e.parameter['url_' + c.key] || '').trim();
      var sheetName = (e.parameter['sheet_' + c.key] || c.label).trim();
      props.setProperty('SPREADSHEET_URL_' + c.key, url);
      props.setProperty('SHEET_NAME_' + c.key, sheetName);
    });
  }

  return HtmlService
    .createHtmlOutput(renderPanel_(props, hasAnyParam))
    .setTitle('Panel NotifLogger')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1');
}

/** Status koneksi tiap kategori untuk ditampilkan di aplikasi Android (read-only). */
function buildStatus_(props) {
  var status = {};
  CATEGORIES.forEach(function (c) {
    var url = props.getProperty('SPREADSHEET_URL_' + c.key) || '';
    var sheetName = props.getProperty('SHEET_NAME_' + c.key) || c.label;
    var id = url ? extractSpreadsheetId_(url) : null;
    var docName = '';
    var connected = false;

    if (id) {
      try {
        docName = SpreadsheetApp.openById(id).getName();
        connected = true;
      } catch (err) {
        docName = '';
        connected = false;
      }
    }

    status[c.key] = { docName: docName, sheetName: sheetName, connected: connected };
  });
  return status;
}

function renderPanel_(props, justSaved) {
  var notice = justSaved
    ? '<p class="notice">Tersimpan. Semua HP otomatis memakai pengaturan baru ini.</p>'
    : '';

  var rows = CATEGORIES.map(function (c) {
    var url = props.getProperty('SPREADSHEET_URL_' + c.key) || '';
    var sheetName = props.getProperty('SHEET_NAME_' + c.key) || c.label;
    return '<fieldset>' +
      '<legend>' + c.label + '</legend>' +
      '<label>Link Google Sheet terhubung</label>' +
      '<input type="text" name="url_' + c.key + '" value="' + escapeHtml_(url) + '" ' +
      'placeholder="https://docs.google.com/spreadsheets/d/xxxxx/edit">' +
      '<label>Nama Sheet/Tab terhubung</label>' +
      '<input type="text" name="sheet_' + c.key + '" value="' + escapeHtml_(sheetName) + '" ' +
      'placeholder="' + c.label + '">' +
      '</fieldset>';
  }).join('');

  return '<!DOCTYPE html><html><head><style>' +
    'body{font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:40px auto;padding:0 16px;color:#222}' +
    'h2{margin-bottom:4px}' +
    '.notice{background:#e8f5e9;color:#1b5e20;padding:10px 12px;border-radius:6px}' +
    'fieldset{margin-top:16px;border:1px solid #ddd;border-radius:6px;padding:12px}' +
    'legend{font-weight:bold;padding:0 6px}' +
    'label{display:block;margin-top:10px;font-size:13px;font-weight:bold}' +
    'input{width:100%;padding:8px;box-sizing:border-box;margin-top:4px;font-size:14px;' +
    'border:1px solid #ccc;border-radius:4px}' +
    'button{margin-top:24px;padding:12px 24px;background:#1b5e20;color:#fff;border:none;' +
    'border-radius:4px;font-size:14px;cursor:pointer}' +
    'small{color:#666}' +
    '</style></head><body>' +
    '<h2>Panel NotifLogger</h2>' +
    '<small>Hubungkan tiap bank/e-wallet ke Google Sheet & tab tujuannya masing-masing.</small>' +
    notice +
    '<form method="get">' +
    rows +
    '<button type="submit">Simpan Semua</button>' +
    '</form>' +
    '</body></html>';
}

function escapeHtml_(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function extractSpreadsheetId_(url) {
  var match = url.match(/[-\w]{25,}/);
  return match ? match[0] : null;
}

function doPost(e) {
  var result = { status: 'error', message: 'unknown error' };

  try {
    var body = JSON.parse(e.postData.contents);
    var category = String(body.category || '').toUpperCase();
    var props = PropertiesService.getScriptProperties();

    var categoryDef = CATEGORIES.filter(function (c) { return c.key === category; })[0];
    if (!categoryDef) {
      result.message = 'Kategori aplikasi tidak dikenali: ' + category;
      return jsonResponse_(result);
    }

    var spreadsheetUrl = props.getProperty('SPREADSHEET_URL_' + category);
    var sheetName = props.getProperty('SHEET_NAME_' + category) || categoryDef.label;

    if (!spreadsheetUrl) {
      result.message = 'Belum ada Google Sheet untuk ' + categoryDef.label +
        '. Buka URL Web App ini di browser untuk mengatur panel dulu.';
      return jsonResponse_(result);
    }

    var id = extractSpreadsheetId_(spreadsheetUrl);
    if (!id) {
      result.message = 'Link Google Sheet untuk ' + categoryDef.label + ' tidak valid.';
      return jsonResponse_(result);
    }

    var ss = SpreadsheetApp.openById(id);
    var sheet = ss.getSheetByName(sheetName);
    if (!sheet) {
      sheet = ss.insertSheet(sheetName);
      sheet.appendRow([
        'Waktu Diterima', 'Waktu Notifikasi', 'Aplikasi', 'Paket Aplikasi',
        'Judul', 'Isi Notifikasi', 'Nominal', 'Jenis'
      ]);
      sheet.setFrozenRows(1);
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
    result.message = 'Tercatat ke ' + categoryDef.label;
  } catch (err) {
    result.message = err.toString();
  }

  return jsonResponse_(result);
}

function jsonResponse_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
