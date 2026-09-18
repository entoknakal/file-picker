# FilePicker Plugin for NativePHP Mobile

FilePicker plugin for Android only

## Installation

```bash
composer require andryzulfikar/file-picker
```

## Usage

```php
use Andryzulfikar\FilePicker\Facades\FilePicker;

// Execute functionality
$files = FilePicker::open([
    'types' => ['image/*', 'application/pdf'],
]);

if (!empty($files)) {
    $path =$files[0]['path'];
    $name =$files[0]['name'];
}
```

## License

MIT