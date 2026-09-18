<?php

namespace entoknakal\FilePicker\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

class FilePickerCopy
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public string $sourcePath,               // Absolute path file temporary dari cache Android
        public string $originalName,             // Nama asli file (misal: "data.ztss")
        public string $targetFolder = 'ztssUpload', // Subfolder tujuan
        public string $disk = 'mobile_public'    // Storage disk tujuan
    ) {}
}