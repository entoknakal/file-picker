# FilePicker Plugin for NativePHP Mobile

FilePicker plugin for Android only

## Installation
1. Install file-picker
```bash
composer require entoknakal/file-picker
```
2. Ensure you have published the NativeServiceProvider:
```bash
php artisan vendor:publish --tag=nativephp-plugins-provider
```
3. Register the plugin 
```bash
php artisan native:plugin:register entoknakal/file-picker
```
4. Check that NativePHP sees the plugin
```bash
php artisan native:plugin:list
```

## Usage

```php
use entoknakal\FilePicker\Facades\FilePicker;

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