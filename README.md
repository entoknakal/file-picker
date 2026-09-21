# FilePicker Plugin for NativePHP Mobile

A FilePicker plugin designed for NativePHP Mobile (Android only).

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

Create a new file named `FileSelector.php` inside the `app/NativeComponents` directory:

```php
<?php

namespace App\NativeComponents;

use entoknakal\FilePicker\Events\FilePickerCopy;
use entoknakal\FilePicker\Facades\FilePicker;
use Illuminate\Support\Facades\Storage;
use Illuminate\View\View;
use Native\Mobile\Edge\NativeComponent;

class FileSelector extends NativeComponent
{
    public ?string $filePath = null;
    public ?string $fileName = null;
    public ?string $mimeType = null;
    public ?string $fileSize = null;

    public array $allowedTypes = ['application/pdf', 'photos'];

    public ?string $allowedTypesString = null;
    public ?string $extension = null;

    public bool $status = true;
    public ?string $errorMessage = null;

    public ?string $storage_path = null;
    public ?string $storage_public = null;

    public ?string $imagesFilePath = null;

    public function selectFile(): void
    {
        $this->errorMessage = null;
        $this->status = true;

        try {
            // Initialize and pass the 'types' criteria to the Kotlin layer
            $fileData = FilePicker::open([
                'types' => $this->allowedTypes,
            ]);

            // Handle case where user cancels the file selection UI in Android
            if (empty($fileData)) {
                return;
            }

            // Capture any explicit errors returned by the Kotlin bridge
            if (isset($fileData['error'])) {
                $this->status = false;
                $this->errorMessage = $fileData['error'];
                return;
            }

            if (!isset($fileData['path'])) {
                return;
            }

            // Instantly dispatch event once the file processing from Kotlin finishes
            if (!empty($fileData['path']) && file_exists($fileData['path'])) {
                FilePickerCopy::dispatch(
                    $fileData['path'],
                    $fileData['name']
                );
            }

            // Map valid payload elements returned from successful Kotlin validation
            $this->fileName = $fileData['name'] ?? null;
            $this->extension = strtolower(pathinfo($fileData['path'], PATHINFO_EXTENSION));
            $this->mimeType = $fileData['mime_type'] ?? null;
            $this->fileSize = $fileData['size'] ?? null;
            $this->allowedTypesString = implode(', ', $this->allowedTypes);

            $this->storage_path = storage_path('');
            $this->storage_public = Storage::disk('mobile_public')->path('');

            // Reference the new copied destination within public storage area
            $newPath = $this->storage_public . 'uploadedFiles/' . $this->fileName;

            if (file_exists($newPath)) {
                $this->filePath = $newPath;
                // Provide physical file path direct to the native:image reader element
                $this->imagesFilePath = $newPath;
            } else {
                $this->filePath = null;
                $this->imagesFilePath = null;
            }
        } catch (\Throwable $e) {
            // CATCH KOTLIN CONSTRAINTS AND EXCEPTIONS:
            // Throws automatically if the user chooses a restricted extension (e.g. PNG/APK)
            // matching criteria errors originating from 'processAndCopyUri' in Kotlin.
            $this->status = false;
            $this->filePath = null;
            $this->errorMessage = $e->getMessage();
        }
    }

    public function render(): View
    {
        return view('native.file-selector');
    }
}
```

### 2. Create the Blade View Template

Create a file named `file-selector.blade.php` inside the `resources/views/native` directory:

```html
<native:scroll-view fill class="bg-theme-background">
    <!-- ======================================================= -->
    <!-- FILE PICKER PLUGIN COMPONENT INTERFACE                  -->
    <!-- ======================================================= -->
    <column class="gap-2 mt-6 w-full">
        <text class="text-md font-semibold text-theme-on-background">FilePicker Plugin Tester</text>

        <!-- Use native:button to trigger the selectFile logic wrapper -->
        <native:button label="Open File Manager" variant="secondary" @press="selectFile" />

        <text class="text-md font-semibold text-theme-on-background">Status: {{ \$status }}</text>

         @if(\$status && \$filePath)
            <vstack class="p-4 bg-zinc-100 dark:bg-zinc-800 rounded-lg mt-2 gap-1 w-full border border-zinc-200">
                <text class="font-bold text-zinc-900 dark:text-white">Selected File:</text>
                <text class="text-sm text-zinc-700 dark:text-zinc-300">Allowed Type String: {{ \$allowedTypesString }}</text>
                <text class="text-sm text-zinc-700 dark:text-zinc-300">Extension: .{{ \$extension }}</text>
                <text class="text-sm text-zinc-700 dark:text-zinc-300 font-medium">Name: {{ \$fileName }}</text>
                <text class="text-xs text-zinc-500 dark:text-zinc-400">Path: {{ \$filePath }}</text>
                <text class="text-xs text-zinc-500 dark:text-zinc-400">Mime Type: {{ \$mimeType }}</text>
                <text class="text-xs text-zinc-500 dark:text-zinc-400">Size: {{ number_format(\$fileSize / 1024, 2) }} KB</text>
                <text class="text-xs text-zinc-500 dark:text-zinc-400">Storage path: {{ \$storage_path }}</text>
                <text class="text-xs text-zinc-500 dark:text-zinc-400">Storage public: {{ \$storage_public }}</text>

                <!-- Conditional Image Preview block container -->
                @if(in_array(\$extension, ['jpg', 'jpeg', 'png', 'webp', 'gif']))
                    <column class="gap-1 py-2">
                        <native:image :src="\$imagesFilePath" :fit="1" class="rounded-xl w-full h-48" />
                    </column>
                @endif
                <text class="text-xs text-zinc-500 dark:text-zinc-400">Image file path: {{ \$imagesFilePath }}</text>
            </vstack>
        @endif
    </column>
</native:scroll-view>
```

### 3. File Processing & Asynchronous Copy via `FilePickerCopy`

When a file is successfully selected on Android, the plugin returns an absolute path referencing the Android sandbox cache directory. To automate transferring this temporary asset safely into your app's standard storage disks, dispatch the `FilePickerCopy` event.

#### Dispatching with Custom Parameters
By default, triggering `FilePickerCopy::dispatch($path, $name)` moves your asset into a folder named `ztssUpload` within the `mobile_public` disk. You can customize the destination variables when constructing the event:

```php
use entoknakal\FilePicker\Events\FilePickerCopy;

FilePickerCopy::dispatch(
    sourcePath: $fileData['path'],       // Absolute Android system cache temporary path
    originalName: $fileData['name'],     // Desired target filename (e.g., "document.pdf")
    targetFolder: 'myCustomFolder',      // Subfolder directory name (defaults to 'ztssUpload')
    disk: 'local'                        // Configured Laravel Storage Disk name (defaults to 'mobile_public')
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
| `zip` | `application/zip`, `application/x-zip-compressed` |
| `rar` | `application/vnd.rar`, `application/x-rar-compressed` |
| `word`, `doc`, `docx` | `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| `excel`, `xls`, `xlsx` | `application/vnd.ms-excel`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `ppt`, `pptx`, `powerpoint` | `application/vnd.ms-powerpoint`, `application/vnd.openxmlformats-officedocument.presentationml.presentation` |
| `txt`, `text` | `text/plain` |
| `csv` | `text/csv` |

*Note: If a custom standalone extension or unrecognized MIME string is processed and cannot be resolved by the device's standard `MimeTypeMap` registry, the Kotlin backend gracefully defaults to `application/octet-stream` or `application/$ext` to prevent execution crashes.*

## Donate

If this plugin helped you save time or brought value to your projects, consider supporting my work! Your donations will go directly toward upgrading my hardware setup (laptop/PC) so I can keep developing innovative tools.

[![Buy Me A Coffee](https://buymeacoffee.com☕&slug=entoknakal&button_colour=FFDD00&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=ffffff)](https://buymeacoffee.com)

## License

The MIT License (MIT). Please see the License File for more information.
