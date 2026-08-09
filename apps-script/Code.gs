/**
 * Backend pencatat notifikasi bank/e-wallet ke Google Sheets, dengan Panel
 * admin (bisa dibuka dari browser komputer ATAU dari aplikasi Android)
 * untuk memilih Spreadsheet & Sheet/tab tujuan tiap bank/e-wallet lewat
 * dropdown -- tidak perlu paste link sama sekali.
 *
 * Struktur: BCA -> pilih Spreadsheet -> pilih Sheet, BRI -> pilih
 * Spreadsheet -> pilih Sheet, dst -- semuanya bisa diganti kapan saja tanpa
 * mengubah apa pun di aplikasi Android (aplikasi otomatis ambil pengaturan
 * terbaru tiap kali membuka layar konfigurasi).
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
 * 4. Buka URL yang sama itu di browser (Panel admin), tambahkan Spreadsheet
 *    yang boleh dipilih lewat kotak "Tambah dari link Google Sheet" (cuma
 *    Spreadsheet yang kamu tambahkan di sini yang akan muncul di dropdown --
 *    bukan otomatis seluruh isi Drive-mu), lalu pilih Spreadsheet & Sheet
 *    tujuan tiap bank/e-wallet dari dropdown, baik lewat Panel ini maupun
 *    lewat tombol "Pilih Aplikasi" di aplikasi Android.
 */

// Naikkan setiap kali Code.gs diubah -- dipakai untuk memastikan lewat curl
// (?format=json) bahwa versi yang benar-benar aktif di deployment sudah
// yang terbaru, bukan versi lama yang ke-cache.
var SCRIPT_VERSION = 'fix-bca-idr-format-16';

var CATEGORIES = [
  { key: 'BCA', label: 'BCA' },
  { key: 'BRI', label: 'BRI' },
  { key: 'MANDIRI', label: 'Mandiri' },
  { key: 'BNI', label: 'BNI' },
  { key: 'DANA', label: 'DANA' },
  { key: 'OVO', label: 'OVO' },
  { key: 'LINKAJA', label: 'LinkAja' },
  { key: 'GOPAY', label: 'GoPay' },
  { key: 'AUTOPASTE', label: 'AUTOPASTE' }
];

function doGet(e) {
  var action = e.parameter.action || '';

  if (e.parameter.format === 'json') {
    if (action === 'list_spreadsheets') return jsonResponse_(listSpreadsheets_());
    if (action === 'list_sheets') return jsonResponse_(listSheetNames_(e.parameter.spreadsheetId || ''));
    if (action === 'inspect_sheet') {
      return jsonResponse_(inspectSheet_(
        e.parameter.spreadsheetId || '',
        e.parameter.sheetName || '',
        e.parameter.row ? Number(e.parameter.row) : null,
        e.parameter.count ? Number(e.parameter.count) : null
      ));
    }
    return jsonResponse_(buildStatus_());
  }

  return HtmlService
    .createHtmlOutput(renderPanel_())
    .setTitle('Panel NotifLogger')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1');
}

function doPost(e) {
  var body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (err) {
    return jsonResponse_({ status: 'error', message: 'Body tidak valid: ' + err });
  }

  if (body.action === 'save_config') {
    return saveConfig_(body.configs || {});
  }
  if (body.action === 'add_spreadsheet') {
    return jsonResponse_(addAllowedSpreadsheet_(body.url || ''));
  }
  if (body.action === 'remove_spreadsheet') {
    return jsonResponse_(removeAllowedSpreadsheet_(body.spreadsheetId || ''));
  }
  if (body.action === 'log_manual') {
    return logManualEntry_(body);
  }

  return logTransaction_(body);
}

/**
 * Spreadsheet yang boleh muncul di dropdown -- daftar putih yang dikelola
 * manual lewat Panel (bukan otomatis dari seluruh Drive), supaya nama
 * dokumen lain di Drive-mu tidak ikut terekspos ke siapa pun yang tahu
 * URL Web App ini.
 */
function getAllowedSpreadsheets_() {
  var raw = PropertiesService.getScriptProperties().getProperty('ALLOWED_SPREADSHEETS');
  if (!raw) return [];
  try {
    return JSON.parse(raw);
  } catch (err) {
    return [];
  }
}

function saveAllowedSpreadsheets_(list) {
  PropertiesService.getScriptProperties().setProperty('ALLOWED_SPREADSHEETS', JSON.stringify(list));
}

function extractSpreadsheetId_(url) {
  var match = String(url).match(/[-\w]{25,}/);
  return match ? match[0] : null;
}

function addAllowedSpreadsheet_(url) {
  var id = extractSpreadsheetId_(url);
  if (!id) {
    return { status: 'error', message: 'Link Google Sheet tidak valid' };
  }

  var name;
  try {
    name = SpreadsheetApp.openById(id).getName();
  } catch (err) {
    return { status: 'error', message: 'Tidak bisa membuka Spreadsheet ini: ' + err };
  }

  var list = getAllowedSpreadsheets_();
  if (!list.some(function (s) { return s.id === id; })) {
    list.push({ id: id, name: name });
    saveAllowedSpreadsheets_(list);
  }

  return { status: 'ok', message: 'Ditambahkan: ' + name, spreadsheets: listSpreadsheets_() };
}

function removeAllowedSpreadsheet_(id) {
  var list = getAllowedSpreadsheets_().filter(function (s) { return s.id !== id; });
  saveAllowedSpreadsheets_(list);
  return { status: 'ok', message: 'Dihapus', spreadsheets: listSpreadsheets_() };
}

/** Daftar Spreadsheet yang sudah di-allow-list, untuk dropdown. */
function listSpreadsheets_() {
  return getAllowedSpreadsheets_().sort(function (a, b) { return a.name.localeCompare(b.name); });
}

/** Nama-nama tab/sheet di dalam satu Spreadsheet, untuk dropdown. */
function listSheetNames_(spreadsheetId) {
  if (!spreadsheetId) return [];
  try {
    return SpreadsheetApp.openById(spreadsheetId).getSheets().map(function (s) {
      return s.getName();
    });
  } catch (err) {
    return [];
  }
}

/**
 * Diagnostik read-only: intip nilai, formula, dan merge cell di baris paling
 * atas dan di sekitar baris tempat lastRowInColumnA_ akan menulis --
 * dipakai untuk memahami struktur template sebelum mengubah logika
 * penulisan baris, tanpa mengubah data apa pun.
 */
function inspectSheet_(spreadsheetId, sheetName, customRow, customCount) {
  if (!spreadsheetId || !sheetName) {
    return { error: 'spreadsheetId dan sheetName wajib diisi' };
  }

  var ss = SpreadsheetApp.openById(spreadsheetId);
  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) return { error: 'Sheet tidak ditemukan' };

  var numCols = Math.max(sheet.getLastColumn(), 12);
  var boundaryRow = lastRowInColumnA_(sheet) + 1;

  function snapshot(startRow, count) {
    startRow = Math.max(1, startRow);
    var range = sheet.getRange(startRow, 1, count, numCols);
    return {
      startRow: startRow,
      values: range.getValues(),
      formulas: range.getFormulas(),
      merges: range.getMergedRanges().map(function (r) { return r.getA1Notation(); })
    };
  }

  var result = {
    numCols: numCols,
    lastRow: sheet.getLastRow(),
    boundaryRow: boundaryRow,
    top: snapshot(1, 6),
    aroundBoundary: snapshot(boundaryRow - 5, 10)
  };

  // row/count opsional lewat query string (?row=7&count=5) untuk intip baris
  // manapun tanpa terikat jendela "top"/"aroundBoundary" bawaan.
  if (customRow) {
    result.custom = snapshot(customRow, customCount || 5);
  }

  return result;
}

/** Status koneksi tiap kategori (Spreadsheet & Sheet yang sedang aktif). */
function buildStatus_() {
  var props = PropertiesService.getScriptProperties();
  var status = {};

  CATEGORIES.forEach(function (c) {
    var spreadsheetId = props.getProperty('SPREADSHEET_ID_' + c.key) || '';
    var sheetName = props.getProperty('SHEET_NAME_' + c.key) || '';
    var spreadsheetName = '';
    var connected = false;

    if (spreadsheetId) {
      try {
        spreadsheetName = SpreadsheetApp.openById(spreadsheetId).getName();
        connected = true;
      } catch (err) {
        connected = false;
      }
    }

    status[c.key] = {
      spreadsheetId: spreadsheetId,
      spreadsheetName: spreadsheetName,
      sheetName: sheetName,
      connected: connected
    };
  });

  status._version = SCRIPT_VERSION;
  return status;
}

function saveConfig_(configs) {
  var props = PropertiesService.getScriptProperties();

  CATEGORIES.forEach(function (c) {
    var cfg = configs[c.key];
    if (!cfg) return;
    props.setProperty('SPREADSHEET_ID_' + c.key, cfg.spreadsheetId || '');
    props.setProperty('SHEET_NAME_' + c.key, cfg.sheetName || '');
  });

  return jsonResponse_({ status: 'ok', message: 'Tersimpan' });
}

var TIMEZONE = 'Asia/Jakarta';
var WARNA_MASUK = '#00FF00';

/** Ambil angka murni dari string nominal, mis. "Rp1.234.000" -> 1234000. */
function ambilAngka_(rp) {
  return Number(String(rp || '').replace(/[^0-9]/g, '')) || 0;
}

/**
 * Ambil bagian "keterangan" dari notifikasi, mis. dari:
 * "...KET.:ATMLTRPRM B9527 000547856 52920100897450"
 * jadi "ATMLTRPRM B9527 000547856" (nomor rekening panjang di akhir dibuang).
 * Juga menangani format notifikasi app BCA yang lebih baru, mis.
 * "Pemasukan sebesar IDR 5,000.00 dari **NI SETI****TI di kategori
 * Transfer Rekening" -> "**NI SETI****TI" (nama pengirim, sudah termasuk
 * penyamaran bintang dari BCA sendiri, bukan hasil parsing yang salah).
 * Kalau tidak ada pola yang cocok, pakai teks aslinya apa adanya.
 */
function parseKeterangan_(text) {
  if (!text) return '';
  var str = String(text);

  var withoutTrailingAccountNumber = str.match(/KET[.:]+\s*(.+?)\s+\d{10,}\s*$/i);
  if (withoutTrailingAccountNumber) return withoutTrailingAccountNumber[1].trim();

  var simple = str.match(/KET[.:]+\s*(.+)$/i);
  if (simple) return simple[1].trim();

  var dariKategori = str.match(/\bdari\s+(.+?)\s+di kategori\b/i);
  if (dariKategori) return dariKategori[1].trim();

  return str;
}

/**
 * Baris terakhir yang benar-benar berisi data di kolom A. Dipakai alih-alih
 * sheet.getLastRow() biasa karena sheet dengan format/formula bawaan (mis.
 * template kas) bisa membuat getLastRow() mengira ratusan baris kosong di
 * bawahnya "terpakai", sehingga data baru nyasar jauh ke bawah. Sekarang
 * cuma dipakai untuk diagnostik (inspect_sheet) -- penulisan data asli
 * pakai firstEmptyRowFrom_ (lihat di bawah).
 */
function lastRowInColumnA_(sheet) {
  var lastRow = sheet.getLastRow();
  if (lastRow === 0) return 0;
  var values = sheet.getRange(1, 1, lastRow, 1).getValues();
  for (var i = values.length - 1; i >= 0; i--) {
    if (values[i][0] !== '' && values[i][0] !== null) {
      return i + 1;
    }
  }
  return 0;
}

/**
 * Baris kosong PERTAMA di kolom A mulai dari startRow. Beda dengan
 * lastRowInColumnA_ (yang mencari baris terisi terakhir di seluruh sheet),
 * fungsi ini kebal terhadap baris "nyasar" jauh di bawah -- kalau ada celah
 * kosong di atas baris nyasar itu, celah itu yang dipakai duluan. Ini yang
 * membuat transaksi baru terisi berurutan dari dekat baris atas (bukan
 * loncat ke bawah baris terisi terakhir).
 */
function firstEmptyRowFrom_(sheet, startRow) {
  var lastRow = sheet.getLastRow();
  if (lastRow < startRow) return startRow;
  var values = sheet.getRange(startRow, 1, lastRow - startRow + 1, 1).getValues();
  for (var i = 0; i < values.length; i++) {
    if (values[i][0] === '' || values[i][0] === null) {
      return startRow + i;
    }
  }
  return lastRow + 1;
}

/**
 * Beberapa sheet (mis. hasil duplikat tab operasional asli) punya banner
 * cell gabungan (merge) di baris atas -- bukan baris 4 kosong seperti
 * template kas standar. Menulis ke sel yang jadi bagian dari merge (selain
 * pojok kiri-atasnya) diam-diam tidak tersimpan (tidak error, tapi juga
 * tidak ada perubahan), jadi minRow harus dilompati sampai lewat merge
 * vertikal terakhir dulu sebelum firstEmptyRowFrom_ mulai mencari baris
 * kosong. Merge satu baris (mis. header per-kolom di baris 2 pada template
 * standar) tidak masalah, hanya merge yang membentang lebih dari satu baris
 * yang perlu dilompati.
 */
function firstUsableRowFrom_(sheet, minRow) {
  var candidate = minRow;
  for (var guard = 0; guard < 50; guard++) {
    var merges = sheet.getRange(candidate, 1, 1, sheet.getMaxColumns()).getMergedRanges();
    var nextRow = candidate;
    merges.forEach(function (m) {
      var mergeEnd = m.getRow() + m.getNumRows() - 1;
      if (m.getNumRows() > 1) {
        nextRow = Math.max(nextRow, mergeEnd + 1);
      }
    });
    if (nextRow === candidate) return candidate;
    candidate = nextRow;
  }
  return candidate;
}

function logTransaction_(body) {
  var result = { status: 'error', message: 'unknown error' };

  try {
    var category = String(body.category || '').toUpperCase();
    var props = PropertiesService.getScriptProperties();

    var categoryDef = CATEGORIES.filter(function (c) { return c.key === category; })[0];
    if (!categoryDef) {
      result.message = 'Kategori aplikasi tidak dikenali: ' + category;
      return jsonResponse_(result);
    }

    var jenis = String(body.type || '').toLowerCase();
    if (jenis !== 'masuk') {
      result.status = 'ignored';
      result.message = 'Bukan notifikasi uang masuk, diabaikan';
      return jsonResponse_(result);
    }

    var spreadsheetId = props.getProperty('SPREADSHEET_ID_' + category);
    var sheetName = props.getProperty('SHEET_NAME_' + category) || categoryDef.label;

    if (!spreadsheetId) {
      result.message = 'Belum ada Spreadsheet untuk ' + categoryDef.label +
        '. Atur dulu lewat "Pilih Aplikasi" di aplikasi Android atau Panel di browser.';
      return jsonResponse_(result);
    }

    var ss = SpreadsheetApp.openById(spreadsheetId);
    var sheet = ss.getSheetByName(sheetName);
    var wasJustCreated = false;
    if (!sheet) {
      sheet = ss.insertSheet(sheetName);
      sheet.appendRow([
        'Tanggal', 'Kode', 'Jam Catat', 'Jam Kasih', 'User ID',
        'Nama Rek Bank', 'Credit', 'Saldo Bank', 'Debit'
      ]);
      sheet.setFrozenRows(1);
      wasJustCreated = true;
    }

    var now = new Date();
    var waktuNotifikasi = body.timestamp ? new Date(body.timestamp) : now;
    // Baris 4 dst di template kas sudah punya formula Saldo Bank/Pending/
    // TERPROSES yang berantai ke baris tepat di atasnya sejak awal (dibuat
    // manual oleh pemilik sheet, bukan oleh NotifLogger). Menulis berurutan
    // ke baris kosong pertama (bukan menyisipkan) memakai rantai formula
    // yang sudah ada apa adanya -- urutannya otomatis kronologis (transaksi
    // pertama di atas, berikutnya di bawahnya), tanpa perlu menyusun ulang
    // formula sama sekali.
    var targetRow = wasJustCreated ? 2 : firstEmptyRowFrom_(sheet, firstUsableRowFrom_(sheet, 4));
    var namaRekBank = parseKeterangan_(body.text);
    var nominal = ambilAngka_(body.amount);

    // Kolom B (OP Proses), D (Jam Kasih), dan E (User ID) sengaja dikosongkan
    // untuk transaksi otomatis -- Jam Kasih baru terisi manual lewat onEdit
    // begitu User ID diisi orang. Ditulis SEKALIGUS satu baris (A..G) lewat
    // setValues -- bukan 4 panggilan getRange/setValue terpisah seperti
    // sebelumnya -- supaya lebih cepat (tiap panggilan Range API di Apps
    // Script punya overhead sendiri). Nama Rek Bank & Credit sudah terisi ->
    // Jam Catat otomatis terisi juga di kolom yang sama, sama seperti aturan
    // onEdit (yang tidak terpicu untuk penulisan otomatis dari script).
    sheet.getRange(targetRow, 1, 1, 7).setValues([[
      Utilities.formatDate(waktuNotifikasi, TIMEZONE, 'dd/MM/yyyy'),
      '',
      Utilities.formatDate(now, TIMEZONE, 'HH:mm:ss'),
      '',
      '',
      namaRekBank,
      nominal
    ]]);
    sheet.getRange(targetRow, 6, 1, 2).setBackground(WARNA_MASUK);

    result.status = 'ok';
    result.message = 'Tercatat ke ' + categoryDef.label;
  } catch (err) {
    result.message = err.toString();
  }

  return jsonResponse_(result);
}

/**
 * Tulis satu baris transaksi dari tools "bot-autopaste" (klik desktop, bukan
 * notifikasi HP) -- sumbernya sudah berupa userId/nama/nominal siap pakai
 * (diparse dari clipboard di sisi tools, tidak perlu parseKeterangan_/type
 * Masuk-Keluar seperti logTransaction_). Kolom E (User ID) diisi identitas
 * yang diambil dari baris pertama clipboard (mis. "AHERIMUSTAFA07"), beda
 * per transaksi -- bukan nama kategori. Jam Kasih diisi bareng Jam Catat
 * (bukan lewat onEdit) karena transaksi yang di-paste sudah berstatus
 * "DP Approve" -- dianggap sudah dikonfirmasi saat tombol diklik.
 */
function logManualEntry_(body) {
  var result = { status: 'error', message: 'unknown error' };

  try {
    var category = String(body.category || '').toUpperCase();
    var props = PropertiesService.getScriptProperties();

    var categoryDef = CATEGORIES.filter(function (c) { return c.key === category; })[0];
    if (!categoryDef) {
      result.message = 'Kategori tidak dikenali: ' + category;
      return jsonResponse_(result);
    }

    var spreadsheetId = props.getProperty('SPREADSHEET_ID_' + category);
    // Tools bot-autopaste boleh kirim sheetName sendiri (dropdown pilihan
    // TEST 1/TEST 2/SHEET3 dst di sisi tools) untuk menimpa tab default yang
    // dikonfigurasi lewat Panel -- spreadsheet-nya tetap sama.
    var sheetName = String(body.sheetName || '').trim() ||
      props.getProperty('SHEET_NAME_' + category) || categoryDef.label;

    if (!spreadsheetId) {
      result.message = 'Belum ada Spreadsheet untuk ' + categoryDef.label +
        '. Atur dulu lewat Panel di browser.';
      return jsonResponse_(result);
    }

    var ss = SpreadsheetApp.openById(spreadsheetId);
    var sheet = ss.getSheetByName(sheetName);
    var wasJustCreated = false;
    if (!sheet) {
      sheet = ss.insertSheet(sheetName);
      sheet.appendRow([
        'Tanggal', 'Kode', 'Jam Catat', 'Jam Kasih', 'User ID',
        'Nama Rek Bank', 'Credit', 'Saldo Bank', 'Debit'
      ]);
      sheet.setFrozenRows(1);
      wasJustCreated = true;
    }

    var now = new Date();
    var targetRow = wasJustCreated ? 2 : firstEmptyRowFrom_(sheet, firstUsableRowFrom_(sheet, 4));
    var userId = String(body.userId || '').trim();
    var nama = String(body.name || '').trim();
    var nominal = ambilAngka_(body.amount);
    var waktu = Utilities.formatDate(now, TIMEZONE, 'HH:mm:ss');
    // Kolom B (OP Proses) diisi kalau bot-autopaste mengirim opCode (mis.
    // "GAB") -- kalau tidak dikirim, dikosongkan seperti alur notifikasi HP.
    var opCode = String(body.opCode || '').trim();

    // Ditulis sekaligus satu baris (A..G) lewat setValues, bukan 5
    // panggilan getRange/setValue terpisah, supaya lebih cepat.
    sheet.getRange(targetRow, 1, 1, 7).setValues([[
      Utilities.formatDate(now, TIMEZONE, 'dd/MM/yyyy'),
      opCode,
      waktu,
      waktu,
      userId,
      nama,
      nominal
    ]]);
    sheet.getRange(targetRow, 6, 1, 2).setBackground(WARNA_MASUK);

    result.status = 'ok';
    result.message = 'Tercatat ke ' + categoryDef.label;
  } catch (err) {
    result.message = err.toString();
  }

  return jsonResponse_(result);
}

function renderPanel_() {
  var props = PropertiesService.getScriptProperties();
  var spreadsheets = listSpreadsheets_();
  var selfUrl = ScriptApp.getService().getUrl();

  var spreadsheetOptions = spreadsheets.map(function (s) {
    return '<option value="' + s.id + '">' + escapeHtml_(s.name) + '</option>';
  }).join('');

  var initialData = {};
  var rows = CATEGORIES.map(function (c) {
    var spreadsheetId = props.getProperty('SPREADSHEET_ID_' + c.key) || '';
    var sheetName = props.getProperty('SHEET_NAME_' + c.key) || '';
    initialData[c.key] = { spreadsheetId: spreadsheetId, sheetName: sheetName };

    return '<fieldset>' +
      '<legend>' + c.label + '</legend>' +
      '<label>Spreadsheet</label>' +
      '<select class="ss-select" data-key="' + c.key + '">' +
      '<option value="">-- Pilih Spreadsheet --</option>' + spreadsheetOptions +
      '</select>' +
      '<label>Sheet/Tab</label>' +
      '<select class="sheet-select" data-key="' + c.key + '">' +
      '<option value="">-- Pilih Sheet --</option>' +
      '</select>' +
      '</fieldset>';
  }).join('');

  var allowedRows = spreadsheets.map(function (s) {
    return '<li>' + escapeHtml_(s.name) +
      ' <button type="button" class="remove-btn" data-id="' + s.id + '">Hapus</button></li>';
  }).join('');

  return '<!DOCTYPE html><html><head><style>' +
    'body{font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:40px auto;padding:0 16px;color:#222}' +
    'h2{margin-bottom:4px}' +
    '.notice{background:#e8f5e9;color:#1b5e20;padding:10px 12px;border-radius:6px}' +
    'fieldset{margin-top:16px;border:1px solid #ddd;border-radius:6px;padding:12px}' +
    'legend{font-weight:bold;padding:0 6px}' +
    'label{display:block;margin-top:10px;font-size:13px;font-weight:bold}' +
    'select,input[type=text]{width:100%;padding:8px;box-sizing:border-box;margin-top:4px;font-size:14px;' +
    'border:1px solid #ccc;border-radius:4px;background:#fff}' +
    'button{margin-top:24px;padding:12px 24px;background:#1b5e20;color:#fff;border:none;' +
    'border-radius:4px;font-size:14px;cursor:pointer}' +
    '.remove-btn{margin-top:0;padding:4px 10px;font-size:12px;background:#B00020}' +
    'ul{list-style:none;padding:0;margin-top:8px}' +
    'ul li{display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid #eee}' +
    'small{color:#666}' +
    '</style></head><body>' +
    '<h2>Panel NotifLogger</h2>' +
    '<small>Hubungkan tiap bank/e-wallet ke Spreadsheet & Sheet tujuannya masing-masing.</small>' +
    '<div id="notice"></div>' +
    '<fieldset>' +
    '<legend>Daftar Spreadsheet yang boleh dipilih</legend>' +
    '<label>Tambah dari link Google Sheet</label>' +
    '<input type="text" id="newSpreadsheetUrl" placeholder="https://docs.google.com/spreadsheets/d/xxxxx/edit">' +
    '<button type="button" id="addSpreadsheetBtn">Tambah</button>' +
    '<ul id="allowedList">' + allowedRows + '</ul>' +
    '</fieldset>' +
    '<form id="panelForm">' + rows + '<button type="submit">Simpan Semua</button></form>' +
    '<script>' +
    'var SELF_URL = ' + JSON.stringify(selfUrl) + ';' +
    'var INITIAL = ' + JSON.stringify(initialData) + ';' +
    'function postAction(payload) {' +
    '  return fetch(SELF_URL, { method: "POST", headers: { "Content-Type": "text/plain" }, body: JSON.stringify(payload) })' +
    '    .then(function(r){ return r.json(); });' +
    '}' +
    'function loadSheets(key, spreadsheetId, selectedSheet) {' +
    '  var sheetSelect = document.querySelector(".sheet-select[data-key=\\"" + key + "\\"]");' +
    '  if (!spreadsheetId) { sheetSelect.innerHTML = "<option value=\\"\\">-- Pilih Sheet --</option>"; return; }' +
    '  sheetSelect.innerHTML = "<option value=\\"\\">Memuat...</option>";' +
    '  fetch(SELF_URL + "?format=json&action=list_sheets&spreadsheetId=" + encodeURIComponent(spreadsheetId))' +
    '    .then(function(r){ return r.json(); })' +
    '    .then(function(names){' +
    '      sheetSelect.innerHTML = "<option value=\\"\\">-- Pilih Sheet --</option>" +' +
    '        names.map(function(n){ return "<option value=\\"" + n + "\\"" + (n === selectedSheet ? " selected" : "") + ">" + n + "</option>"; }).join("");' +
    '    });' +
    '}' +
    'document.querySelectorAll(".ss-select").forEach(function(sel){' +
    '  var key = sel.getAttribute("data-key");' +
    '  var init = INITIAL[key];' +
    '  if (init && init.spreadsheetId) {' +
    '    sel.value = init.spreadsheetId;' +
    '    loadSheets(key, init.spreadsheetId, init.sheetName);' +
    '  }' +
    '  sel.addEventListener("change", function(){ loadSheets(key, sel.value, ""); });' +
    '});' +
    'document.getElementById("panelForm").addEventListener("submit", function(ev){' +
    '  ev.preventDefault();' +
    '  var configs = {};' +
    '  document.querySelectorAll(".ss-select").forEach(function(sel){' +
    '    var key = sel.getAttribute("data-key");' +
    '    var sheetSel = document.querySelector(".sheet-select[data-key=\\"" + key + "\\"]");' +
    '    configs[key] = { spreadsheetId: sel.value, sheetName: sheetSel.value };' +
    '  });' +
    '  postAction({ action: "save_config", configs: configs })' +
    '    .then(function(res){ document.getElementById("notice").innerHTML = "<p class=\\"notice\\">" + res.message + "</p>"; });' +
    '});' +
    'document.getElementById("addSpreadsheetBtn").addEventListener("click", function(){' +
    '  var url = document.getElementById("newSpreadsheetUrl").value.trim();' +
    '  if (!url) return;' +
    '  postAction({ action: "add_spreadsheet", url: url }).then(function(res){' +
    '    document.getElementById("notice").innerHTML = "<p class=\\"notice\\">" + res.message + "</p>";' +
    '    if (res.status === "ok") location.reload();' +
    '  });' +
    '});' +
    'document.querySelectorAll(".remove-btn").forEach(function(btn){' +
    '  btn.addEventListener("click", function(){' +
    '    postAction({ action: "remove_spreadsheet", spreadsheetId: btn.getAttribute("data-id") }).then(function(){' +
    '      location.reload();' +
    '    });' +
    '  });' +
    '});' +
    '</script>' +
    '</body></html>';
}

function escapeHtml_(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function jsonResponse_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/** Cek apakah sheet ini sedang terhubung sebagai tujuan salah satu dari 8 kategori. */
function isConfiguredSheet_(spreadsheetId, sheetName) {
  var props = PropertiesService.getScriptProperties();
  return CATEGORIES.some(function (c) {
    return props.getProperty('SPREADSHEET_ID_' + c.key) === spreadsheetId &&
      props.getProperty('SHEET_NAME_' + c.key) === sheetName;
  });
}

/**
 * Auto-timestamp Jam Catat & Jam Kasih, berlaku untuk Sheet manapun yang
 * sedang terhubung ke salah satu dari 8 kategori (BCA/BRI/Mandiri/dst) lewat
 * Panel -- bukan dikunci ke satu nama sheet tertentu. Dipicu saat manusia
 * mengedit sel secara langsung di UI Sheets (bukan saat doPost menulis baris
 * dari notifikasi HP -- appendRow/setValues dari script tidak pernah memicu
 * onEdit).
 *
 * Kolom: C=Jam Catat, D=Jam Kasih, E=User ID, F=Nama Rek Bank, G=Credit.
 * - Kalau F (Nama Rek Bank) dan G (Credit) sudah terisi -> C (Jam Catat)
 *   otomatis diisi jam sekarang.
 * - Kalau E (User ID) sudah terisi -> D (Jam Kasih) otomatis diisi jam
 *   sekarang. Kalau E belum terisi, D TIDAK diisi otomatis.
 * - C dan D hanya diisi SEKALI -- kalau sudah ada isinya (baik dari sini
 *   maupun dari doPost notifikasi otomatis), tidak akan ditimpa lagi oleh
 *   edit berikutnya di baris yang sama.
 */
function onEdit(e) {
  var sheet = e.range.getSheet();
  var spreadsheetId = sheet.getParent().getId();
  if (!isConfiguredSheet_(spreadsheetId, sheet.getName())) return;

  var row = e.range.getRow();
  if (row === 1) return;

  var rowValues = sheet.getRange(row, 3, 1, 5).getValues()[0];
  var jamCatat = rowValues[0];
  var jamKasih = rowValues[1];
  var userId = rowValues[2];
  var namaRekBank = rowValues[3];
  var credit = rowValues[4];
  var waktu = Utilities.formatDate(new Date(), TIMEZONE, 'HH:mm:ss');

  if (namaRekBank && credit && !jamCatat) {
    sheet.getRange(row, 3).setValue(waktu);
  }

  if (userId && !jamKasih) {
    sheet.getRange(row, 4).setValue(waktu);
  }
}
