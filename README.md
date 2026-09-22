# FilePicker Plugin for NativePHP Mobile

A FilePicker plugin designed for NativePHP Mobile (Android only).

### Features

* **Single & Multiple Selection Support:** Easily toggle between picking a single file or multiple files simultaneously using the multiple parameter.
* **Flexible File Filtering:** Filter choices using broad aliases (e.g., photos, videos), explicit MIME types (e.g., application/pdf), or specific file extensions.
* **Asynchronous Copying & Stream Handling:** Moves assets safely from temporary Android cache locations into custom Laravel Storage disks using the FilePickerCopy event.
* **Robust Error Handling:** Captured Kotlin constraints, access denials, or criteria mismatches are cleanly returned as customizable error exceptions.

## Dependencies

Before installing, ensure your project meets the following requirements:

* **PHP** 8.3 or higher
* **Laravel** 12.x / 13.x
* **NativePHP Mobile** framework (Android environment configured)
* **Minimum Android version:** 12

## Installation

Follow these steps to install and register the plugin in your project:

1. **Install the package via Composer:**
   ```bash
   composer require entoknakal/file-picker
   ```

2. **Ensure you have published the NativeServiceProvider:**
   ```bash
   php artisan vendor:publish --tag=nativephp-plugins-provider
   ```

3. **Register the plugin:**
   ```bash
   php artisan native:plugin:register entoknakal/file-picker
   ```

4. **Verify that NativePHP recognizes the plugin:**
   ```bash
   php artisan native:plugin:list
   ```

## Usage

To implement the FilePicker in your NativePHP Mobile application, follow these steps to create a NativeComponent and its corresponding Blade view.

### 1. Create the NativeComponent Class

Create or update your component file named `PickFile.php` inside the `app/NativeComponents` directory:

```php
<?php

namespace App\NativeComponents;

use entoknakal\FilePicker\Events\FilePickerCopy;
use entoknakal\FilePicker\Facades\FilePicker;
use Illuminate\Support\Facades\Storage;
use Illuminate\View\View;
use Native\Mobile\Edge\NativeComponent;

class PickFile extends NativeComponent
{
    public array $allowedTypes = ['application/pdf', 'photos'];
    
    // Set to true for multiple selection. Default value is false.
    public bool $multiple = false; 

    public array $selectedFiles = [];

    // First file metadata shortcuts (for single file compatibility)
    public ?string $filePath = null;
    public ?string $fileName = null;
    public ?string $mimeType = null;
    public ?string $fileSize = null;
    public ?string $extension = null;
    public ?string $imagesFilePath = null;

    public ?string $allowedTypesString = null;

    public ?bool $status = null; // Default state is null
    public ?string $errorMessage = null;

    public function selectFile(): void
    {
        $this->errorMessage = null;
        $this->status = null;
        $this->selectedFiles = [];

        try {
            $fileData = FilePicker::open([
                'types' => $this->allowedTypes,
                'multiple' => $this->multiple,
            ]);

            if (empty($fileData)) {
                return;
            }

            if (isset($fileData['error'])) {
                $this->status = false;
                $this->errorMessage = $fileData['error'];
                return;
            }

            // Normalize payload structure
            if (isset($fileData['files']) && is_array($fileData['files'])) {
                $items = $fileData['files'];
            } elseif (isset($fileData) && is_array($fileData)) {
                $items = $fileData;
            } else {
                $items = [$fileData];
            }

            $disk = Storage::disk('mobile_public');
            $this->allowedTypesString = implode(', ', $this->allowedTypes);

            foreach ($items as $index => $item) {
                if (empty($item['path']) || !file_exists($item['path'])) {
                    continue;
                }

                $originalName = $item['name'] ?? ('file_' . time());
                $ext = strtolower(pathinfo($originalName, PATHINFO_EXTENSION));
                $baseName = pathinfo($originalName, PATHINFO_FILENAME);
                $fileName = $baseName . ($ext ? '.' . $ext : '');

                // Stream copy from temporary cache
                FilePickerCopy::dispatch(
                    $item['path'],
                    $fileName,
                    'ztssUpload',
                    'mobile_public'
                );

                $relativePath = 'ztssUpload/' . $fileName;
                $finalPath = $disk->path($relativePath);

                if (!file_exists($finalPath)) {
                    if (!$disk->exists('ztssUpload')) {
                        $disk->makeDirectory('ztssUpload');
                    }
                    copy($item['path'], $finalPath);
                    @unlink($item['path']);
                }

                if (!file_exists($finalPath)) {
                    continue;
                }

                $mimeType = $item['mime_type'] ?? null;
                $isImage = in_array($ext, ['jpg', 'jpeg', 'png', 'webp', 'gif'])
                    || ($mimeType && str_starts_with($mimeType, 'image/'));

                $fileSrc = str_starts_with($finalPath, 'file://') ? $finalPath : 'file://' . $finalPath;

                $this->selectedFiles[] = [
                    'name' => $fileName,
                    'path' => $finalPath,
                    'file_src' => $fileSrc,
                    'extension' => $ext,
                    'mime_type' => $mimeType,
                    'size' => $item['size'] ?? (file_exists($finalPath) ? filesize($finalPath) : 0),
                    'is_image' => $isImage,
                ];
            }

            if (!empty($this->selectedFiles)) {
                $this->status = true;
                $first = $this->selectedFiles[0];
                $this->fileName = $first['name'];
                $this->filePath = $first['path'];
                $this->extension = $first['extension'];
                $this->mimeType = $first['mime_type'];
                $this->fileSize = (string) $first['size'];
                $this->imagesFilePath = $first['file_src'];
            } else {
                $this->status = false;
                $this->errorMessage = 'Failed to save files into the ztssUpload directory.';
            }
        } catch (\Throwable $e) {
            $this->status = false;
            $this->filePath = null;
            $this->selectedFiles = [];
            $this->errorMessage = $e->getMessage();
        }
    }

    public function render(): View
    {
        return view('native.pick-file');
    }
}
```

### 2. Create the Blade View Template

Create or update your template file named `pick-file.blade.php` inside the `resources/views/native` directory:

```html
<native:scroll-view fill class="bg-theme-background">
    <column class="gap-2 mt-6 w-full">
        <text class="text-md font-semibold text-theme-on-background">Penguji Plugin FilePicker</text>

        <text class="text-xs text-zinc-500 dark:text-zinc-400">
            Multiple Mode: {{ $multiple ? 'Enabled (Select Multiple Files)' : 'Disabled (Select Single File)' }}
        </text>

        <native:button label="Open File Manager" variant="secondary" @press="selectFile" />

        @if($status !== null)
            <text class="text-md font-semibold {{ $status ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400' }}">
                Status: {{ $status ? 'Succeed' : 'Failed' }}
            </text>
        @endif

        @if($status === true && !empty($selectedFiles))
            <vstack class="gap-3 mt-2 w-full">
                <text class="font-bold text-zinc-900 dark:text-white">
                    Total Files Selected: {{ count($selectedFiles) }}
                </text>

                @foreach($selectedFiles as $index => $file)
                    <vstack class="p-4 bg-zinc-100 dark:bg-zinc-800 rounded-lg gap-1 w-full border border-zinc-200 dark:border-zinc-700">
                        <text class="font-bold text-zinc-900 dark:text-white">
                            File #{{ $index + 1 }}: {{ $file['name'] }}
                        </text>
                        <text class="text-sm text-zinc-700 dark:text-zinc-300">Allowed Formats: {{ $allowedTypesString }}</text>
                        <text class="text-sm text-zinc-700 dark:text-zinc-300">Extension: .{{ $file['extension'] }}</text>
                        <text class="text-xs text-zinc-500 dark:text-zinc-400">Path: {{ $file['path'] }}</text>
                        <text class="text-xs text-zinc-500 dark:text-zinc-400">Mime Type: {{ $file['mime_type'] }}</text>
                        <text class="text-xs text-zinc-500 dark:text-zinc-400">
                            Size: {{ number_format($file['size'] / 1024, 2) }} KB
                        </text>

                        @if($file['is_image'])
                            <column class="gap-1 py-2">
                                <native:image :src="$file['file_src']" :fit="1" class="rounded-xl w-full h-48" />
                            </column>
                        @endif
                    </vstack>
                @endforeach
            </vstack>
        @endif

        @if($status === false && $errorMessage)
            <vstack class="p-4 bg-red-50 dark:bg-red-950/30 rounded-lg mt-2 gap-2 w-full border border-red-200 dark:border-red-900">
                <hstack class="gap-2 items-center">
                    <text class="text-md font-bold text-red-600 dark:text-red-400">⚠️ Akses Ditolak / Gagal</text>
                </hstack>

```

### Multiple File Selection Configuration

By default, the plugin opens the system file explorer in single-file mode. To enable multiple file selection, configure the $multiple state in your component: 

* **Single Selection (Default):** public bool $multiple = false; (Allows picking only one file).
* **Multiple Selection:** Set public bool $multiple = true; inside your PickFile component before executing FilePicker::open().

### 3. File Processing & Asynchronous Copy via `FilePickerCopy`

When a file is successfully selected on Android, the plugin returns an absolute path referencing the Android sandbox cache directory. To automate transferring this temporary asset safely into your app's standard storage disks, dispatch the `FilePickerCopy` event.

#### Dispatching with Custom Parameters
By default, triggering `FilePickerCopy::dispatch($path, $name)` moves your asset into a folder named `ztssUpload` within the `mobile_public` disk. You can customize the destination variables when constructing the event:

```php
use entoknakal\FilePicker\Events\FilePickerCopy;

FilePickerCopy::dispatch(
    sourcePath: $item['path'],       // Absolute Android system cache temporary path
    originalName: $fileName,         // Desired target filename (e.g., "document.pdf")
    targetFolder: 'ztssUpload',      // Subfolder directory name
    disk: 'mobile_public'            // Configured Laravel Storage Disk name
);
```

#### Event Variable Overview
The `FilePickerCopy` constructor accepts the following properties:

| Variable | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `sourcePath` | `string` | *Required* | The absolute source path pointing to the temporary Android cache location. |
| `originalName` | `string` | *Required* | The original file name used to output and name the target file structure. |
| `targetFolder` | `string` | `'ztssUpload'` | Subfolder pathway path inside the designated disk system where the file should land. |
| `disk` | `string` | `'mobile_public'` | The core file system storage disk target identifier registered under your Laravel environment. |

#### How It Works Under the Hood
Once dispatched, the built-in listener executes these underlying routines:
1. Verifies the existence of the source asset inside the local cache workspace.
2. Dynamically creates the nested `targetFolder` tree structure if it does not exist yet on the selected disk framework.
3. Safely streams file contents using native drivers into your project storage profile.
4. Automatically housekeeps and calls `@unlink()` on the temporary Android cache source upon a successful file transfer event completion.

## Supported File Types

The plugin parses filter inputs flexibly. You can pass broad group aliases, explicit MIME types, or specific extensions to the `$allowedTypes` array configuration:

* **Group Aliases:** If you provide a keyword alias like `photos`, the file picker will automatically restrict choices and validate files matching the `image/*` MIME type scope.
  ```php
  public array $allowedTypes = ['photos']; // Resolves to image/*
  ```
* **Explicit MIME Types:** You can also specify exact strict MIME type identifiers directly within the array parameters.
  ```php
  public array $allowedTypes = ['image/png', 'application/pdf', 'application/octet-stream']; 
  ```

### Predefined Keyword Mapping Reference

The following table outlines how built-in string values are normalized and translated into standard Android targets:

| Keyword Alias / Group | Resolved Android MIME Type Target(s) |
| :--- | :--- |
| `*`, `*/*`, `all` | `*/*` (Allows selection of any file type) |
| `images`, `image`, `photo`, `photos`, `picture`, `pictures` | `image/*` |
| `videos`, `video`, `movie`, `movies` | `video/*` |
| `audios`, `audio`, `sound`, `sounds`, `music` | `audio/*` |
| `pdf` | `application/pdf` |
| `zip` | `application/zip`, <br>`application/x-zip-compressed` |
| `rar` | `application/vnd.rar`, <br>`application/x-rar-compressed` |
| `word`, `doc`, `docx` | `application/msword`, <br>`application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| `excel`, `xls`, `xlsx` | `application/vnd.ms-excel`, <br>`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `ppt`, `pptx`, `powerpoint` | `application/vnd.ms-powerpoint`, <br>`application/vnd.openxmlformats-officedocument.presentationml.presentation` |
| `txt`, `text` | `text/plain` |
| `csv` | `text/csv` |

*Note: If a custom standalone extension or unrecognized MIME string is processed and cannot be resolved by the device's standard `MimeTypeMap` registry, the Kotlin backend gracefully defaults to `application/octet-stream` or `application/$ext` to prevent execution crashes.*

## Buy me a coffee :)

If this plugin helped you save time or brought value to your projects, consider supporting my work! Your donations will go directly toward upgrading my hardware setup so I can keep developing innovative tools.

<a href="https://buymeacoffee.com/entoknakal" target="_blank"><img src="https://cdn.buymeacoffee.com/" alt="Buy Me A Coffee" style="height: 51px !important;width: auto !important;" >Buy me a coffee</a>


Or:

<a href="https://trakteer.id/entoknakal" target="_blank">
  <img width="80" height="80" alt="trakteer-logo-new" src="https://github.com/user-attachments/assets/76c1f2a5-f9b0-455f-8281-c925875888dd" />
   Trakteer Saya :)
</a>

## License

The MIT License (MIT). Please see the License File for more information.
