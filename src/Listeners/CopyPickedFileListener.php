<?php

namespace Andryzulfikar\FilePicker\Listeners;

use Andryzulfikar\FilePicker\Events\FilePickerCopy;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\Log;

class CopyPickedFileListener
{
    /**
     * Memproses pemindahan file dari cache ke storage target.
     */
    public function handle(FilePickerCopy $event): ?string
    {
        // 1. Cek keberadaan file di cache Android
        if (!file_exists($event->sourcePath)) {
            Log::warning("FilePickerCopy: File sumber tidak ditemukan di {$event->sourcePath}");
            return null;
        }

        try {
            // 2. Format path tujuan dengan nama asli file
            $targetFolder = trim($event->targetFolder, '/');
            $destinationPath = $targetFolder . '/' . ltrim($event->originalName, '/');

            // 3. Pastikan direktori tujuan sudah ada
            if (!Storage::disk($event->disk)->exists($targetFolder)) {
                Storage::disk($event->disk)->makeDirectory($targetFolder);
            }

            // 4. Salin isi file ke Storage Disk (mobile_public)
            $saved = Storage::disk($event->disk)->put(
                $destinationPath,
                file_get_contents($event->sourcePath)
            );

            // 5. Jika sukses disalin, hapus file temporary di cache
            if ($saved) {
                @unlink($event->sourcePath);
                return $destinationPath;
            }
        } catch (\Throwable $e) {
            Log::error("FilePickerCopy Error: " . $e->getMessage());
        }

        return null;
    }
}           