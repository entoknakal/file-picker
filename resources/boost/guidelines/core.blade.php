## entoknakal/file-picker

FilePicker plugin for Android only

### Installation

```bash
composer require entoknakal/file-picker
```

### PHP Usage (Livewire/Blade)

Use the `FilePicker` facade:

@verbatim
    <code-snippet name="Using FilePicker Facade" lang="php">
        use entoknakal\FilePicker\Facades\FilePicker;

        // Execute the plugin functionality
        $result = FilePicker::execute(['option1' => 'value']);

        // Get the current status
        $status = FilePicker::getStatus();
    </code-snippet>
@endverbatim

### Available Methods

- `FilePicker::execute()`: Execute the plugin functionality
- `FilePicker::getStatus()`: Get the current status

### Events

- `FilePickerCompleted`: Listen with `#[OnNative(FilePickerCompleted::class)]`

@verbatim
    <code-snippet name="Listening for FilePicker Events" lang="php">
        use Native\Mobile\Attributes\OnNative;
        use entoknakal\FilePicker\Events\FilePickerCompleted;

        #[OnNative(FilePickerCompleted::class)]
        public function handleFilePickerCompleted($result, $id = null)
        {
        // Handle the event
        }
    </code-snippet>
@endverbatim

### JavaScript Usage (Vue/React/Inertia)

@verbatim
    <code-snippet name="Using FilePicker in JavaScript" lang="javascript">
        import { filePicker } from '@entoknakal/file-picker';

        // Execute the plugin functionality
        const result = await filePicker.execute({ option1: 'value' });

        // Get the current status
        const status = await filePicker.getStatus();
    </code-snippet>
@endverbatim