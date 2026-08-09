"""Bot AutoPaste -- baca transaksi dari clipboard, catat otomatis ke Google Sheets.

Alur: buka aplikasi PPOB/admin panel di browser, select & copy detail
transaksi, lalu klik tombol "Baca Clipboard & Catat" di jendela kecil ini.
Tidak perlu isi apa pun manual -- teksnya diparse otomatis lalu dikirim ke
backend NotifLogger (kategori AUTOPASTE) lewat HTTP POST, sama seperti
notifikasi dari aplikasi Android.
"""

import json
import re
import threading
import tkinter as tk
from tkinter import font as tkfont
from tkinter import ttk
from urllib.request import Request, urlopen
from urllib.error import URLError

WEB_APP_URL = (
    "https://script.google.com/macros/s/"
    "AKfycbwmaD_puo3nZPzU5sfC84raKvut2bEkJSXviUO-PVyXY4g02fJ-7gBrRy2VFU6MOUN7mA/exec"
)
CATEGORY = "AUTOPASTE"

# Label channel yang muncul sebagai penanda blok "channel / nama / no HP" di
# clipboard. Nama pengirim yang diambil adalah baris tepat SETELAH channel
# pertama yang ditemukan (blok kedua, kalau ada, diabaikan -- itu biasanya
# nama admin/approver, bukan nama pengirim dana).
CHANNEL_KEYWORDS = {"DANA", "OVO", "GOPAY", "LINKAJA", "BCA", "BRI", "MANDIRI", "BNI"}


class ParseError(Exception):
    pass


def parse_clipboard(text):
    """Ambil (user_id, nama_pengirim, nominal) dari teks clipboard transaksi.

    Aturan (dikonfirmasi dari contoh nyata pengguna):
    - Baris PERTAMA di clipboard adalah identitas/kode akun (mis.
      "AHERIMUSTAFA07", "ARGONTARA") -- beda-beda per transaksi, dicatat apa
      adanya ke kolom User ID.
    - Setelah itu ada beberapa baris "Rp <angka>" berdiri sendiri; yang
      PERTAMA adalah kode/referensi/fee yang diabaikan, yang KEDUA adalah
      nominal transaksi yang sebenarnya.
    - Ada satu atau lebih blok 3 baris berurutan: nama channel (mis. DANA),
      lalu nama pengirim, lalu nomor HP. Blok PERTAMA dipakai (nama
      pengirim), blok berikutnya (biasanya nama admin/approver) diabaikan.
    - Baris "DP Approve : ..." / "DP Reject : ..." dan tanggal/jam diabaikan
      sepenuhnya -- Jam Catat & Jam Kasih diisi otomatis pakai waktu saat
      tombol diklik (transaksi yang di-paste dianggap sudah "DP Approve").
    """
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    if not lines:
        raise ParseError("Clipboard kosong.")

    user_id = lines[0]
    rest = lines[1:]

    rp_lines = [l for l in rest if re.fullmatch(r"Rp\.?\s*[\d.,]+", l, re.IGNORECASE)]
    if len(rp_lines) < 2:
        raise ParseError(
            "Tidak ketemu 2 baris nominal ('Rp ...') di clipboard. "
            "Pastikan sudah copy detail transaksi yang lengkap."
        )
    amount_digits = re.sub(r"[^\d]", "", rp_lines[1])
    if not amount_digits:
        raise ParseError("Baris nominal kedua tidak berisi angka yang valid.")

    name = None
    for i, line in enumerate(rest):
        if line.upper() in CHANNEL_KEYWORDS and i + 1 < len(rest):
            name = rest[i + 1]
            break
    if not name:
        raise ParseError(
            "Tidak ketemu nama pengirim (baris setelah channel seperti DANA/OVO/dst.)."
        )

    return user_id, name, amount_digits


def fetch_json(url):
    with urlopen(url, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_sheet_options():
    """Ambil daftar tab langsung dari spreadsheet yang dikonfigurasi untuk
    kategori AUTOPASTE di Panel -- supaya dropdown ini tidak pernah nyasar
    ke nama tab yang sebenarnya tidak ada (lihat catatan di bawah soal
    backend yang auto-create sheet kalau nama tidak ketemu), dan supaya
    menambah/mengubah pilihan tidak perlu edit kode + build ulang exe.
    """
    status = fetch_json(WEB_APP_URL + "?format=json")
    cat = status.get(CATEGORY) or {}
    spreadsheet_id = cat.get("spreadsheetId") or ""
    if not spreadsheet_id:
        raise RuntimeError(f"Kategori {CATEGORY} belum diatur di Panel.")

    sheets = fetch_json(
        WEB_APP_URL + "?format=json&action=list_sheets&spreadsheetId=" + spreadsheet_id
    )
    if not isinstance(sheets, list) or not sheets:
        raise RuntimeError("Spreadsheet AUTOPASTE tidak punya tab apa pun.")

    # Sengaja TIDAK jatuh ke sheets[0] kalau nama yang dikonfigurasi di Panel
    # sudah tidak ada -- daftar tab produksi ini bercampur antara tab
    # transaksi (mis. BCA_SARAH) dan tab administratif (mis. REKAPAN
    # SUNTIKAN, PENDINGAN), jadi memilihkan salah satu secara sembarangan
    # berisiko menulis transaksi ke tab yang salah tanpa disadari user.
    configured_sheet = cat.get("sheetName") or ""
    default = configured_sheet if configured_sheet in sheets else None
    return sheets, default


def send_to_sheet(user_id, name, amount, sheet_name):
    payload = json.dumps(
        {
            "action": "log_manual",
            "category": CATEGORY,
            "sheetName": sheet_name,
            "userId": user_id,
            "name": name,
            "amount": amount,
        }
    ).encode("utf-8")
    req = Request(WEB_APP_URL, data=payload, headers={"Content-Type": "application/json"})
    with urlopen(req, timeout=60) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    return body


class App:
    def __init__(self, root):
        self.root = root
        root.title("Bot AutoPaste")
        root.attributes("-topmost", True)
        root.resizable(False, False)
        root.configure(bg="#1b1b1b")

        title_font = tkfont.Font(family="Segoe UI", size=12, weight="bold")
        status_font = tkfont.Font(family="Segoe UI", size=10)

        tk.Label(
            root, text="Bot AutoPaste",
            font=title_font, bg="#1b1b1b", fg="#f2c14e", pady=10,
        ).pack(fill="x", padx=16)

        self.btn = tk.Button(
            root, text="\U0001F4CB  Baca Clipboard & Catat", font=title_font,
            bg="#f2c14e", fg="#1b1b1b", activebackground="#d9a93e",
            relief="flat", padx=16, pady=10, command=self.on_click,
        )
        self.btn.pack(fill="x", padx=16, pady=(0, 10))

        sheet_row = tk.Frame(root, bg="#1b1b1b")
        sheet_row.pack(fill="x", padx=16, pady=(0, 10))

        tk.Label(
            sheet_row, text="Sheet tujuan:", font=status_font,
            bg="#1b1b1b", fg="#cccccc",
        ).pack(side="left")

        self.sheet_var = tk.StringVar(value="Memuat...")
        self.sheet_combo = ttk.Combobox(
            sheet_row, textvariable=self.sheet_var, values=[],
            state="disabled", width=14, font=status_font,
        )
        self.sheet_combo.pack(side="left", padx=(8, 0))

        self.refresh_btn = tk.Button(
            sheet_row, text="↻", font=status_font,
            bg="#1b1b1b", fg="#f2c14e", relief="flat", bd=0,
            command=self.load_sheet_options,
        )
        self.refresh_btn.pack(side="left", padx=(4, 0))

        self.status = tk.Label(
            root, text="Memuat daftar sheet...",
            font=status_font, bg="#1b1b1b", fg="#cccccc",
            wraplength=320, justify="left", pady=8,
        )
        self.status.pack(fill="x", padx=16, pady=(0, 14))

        self.load_sheet_options()

    def set_status(self, text, color="#cccccc"):
        self.status.configure(text=text, fg=color)
        self.root.update_idletasks()

    def load_sheet_options(self):
        self.sheet_combo.configure(state="disabled")
        self.sheet_var.set("Memuat...")
        self.set_status("Mengambil daftar sheet dari spreadsheet AUTOPASTE...", "#f2c14e")
        threading.Thread(target=self._load_sheet_options_worker, daemon=True).start()

    def _load_sheet_options_worker(self):
        try:
            sheets, default = fetch_sheet_options()
        except Exception as e:
            self.root.after(0, lambda: self._on_sheets_loaded(None, None, e))
            return
        self.root.after(0, lambda: self._on_sheets_loaded(sheets, default, None))

    def _on_sheets_loaded(self, sheets, default, error):
        if error is not None:
            self.sheet_combo.configure(values=[], state="disabled")
            self.sheet_var.set("(gagal)")
            self.set_status(f"Gagal ambil daftar sheet: {error}. Klik ↻ untuk coba lagi.", "#e05d5d")
            return
        self.sheet_combo.configure(values=sheets, state="readonly")
        if default:
            self.sheet_var.set(default)
            self.set_status("Siap. Copy transaksi lalu klik tombol di atas.", "#cccccc")
        else:
            self.sheet_var.set("")
            self.set_status(
                "Tab yang diatur di Panel sudah tidak ada -- pilih tab tujuan manual dulu.",
                "#f2c14e",
            )

    def on_click(self):
        if str(self.sheet_combo["state"]) != "readonly":
            self.set_status("Daftar sheet belum siap. Klik ↻ dulu.", "#e05d5d")
            return
        if not self.sheet_var.get():
            self.set_status("Pilih tab tujuan di dropdown dulu sebelum kirim.", "#e05d5d")
            return

        try:
            clip = self.root.clipboard_get()
        except tk.TclError:
            self.set_status("Clipboard kosong / bukan teks.", "#e05d5d")
            return

        try:
            user_id, name, amount = parse_clipboard(clip)
        except ParseError as e:
            self.set_status(f"Gagal baca: {e}", "#e05d5d")
            return

        sheet_name = self.sheet_var.get()
        formatted = f"Rp{int(amount):,}".replace(",", ".")
        self.set_status(
            f"Mengirim ke {sheet_name}: {user_id} / {name} - {formatted} ...", "#f2c14e"
        )
        # Kirim di thread terpisah -- urlopen memblokir beberapa detik, dan
        # kalau dijalankan langsung di main thread jendelanya kelihatan macet
        # (tidak bisa digeser/di-drag) selagi menunggu balasan server.
        self.btn.configure(state="disabled")
        threading.Thread(
            target=self._send_worker,
            args=(user_id, name, amount, sheet_name, formatted),
            daemon=True,
        ).start()

    def _send_worker(self, user_id, name, amount, sheet_name, formatted):
        try:
            result = send_to_sheet(user_id, name, amount, sheet_name)
        except URLError as e:
            self.root.after(0, lambda: self._on_send_done(
                False, f"Gagal kirim (jaringan): {e}"
            ))
            return
        if result.get("status") == "ok":
            self.root.after(0, lambda: self._on_send_done(
                True, f"✓ Tercatat ke {sheet_name}: {user_id} / {name} - {formatted}"
            ))
        else:
            self.root.after(0, lambda: self._on_send_done(
                False, f"✗ Ditolak server: {result.get('message')}"
            ))

    def _on_send_done(self, success, message):
        self.set_status(message, "#7dd87d" if success else "#e05d5d")
        self.btn.configure(state="normal")


if __name__ == "__main__":
    root = tk.Tk()
    App(root)
    root.mainloop()
