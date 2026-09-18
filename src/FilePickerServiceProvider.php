<?php

namespace entoknakal\FilePicker;

use Illuminate\Support\ServiceProvider;
use entoknakal\FilePicker\Commands\CopyAssetsCommand;
use entoknakal\FilePicker\Events\FilePickerCopy;
use entoknakal\FilePicker\Listeners\CopyPickedFileListener;
use Illuminate\Support\Facades\Event;

class FilePickerServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(FilePicker::class, function () {
            return new FilePicker();
        });
    }

    public function boot(): void
    {
        // Register plugin hook commands
        if ($this->app->runningInConsole()) {
            $this->commands([
                CopyAssetsCommand::class,
            ]);
        }

        Event::listen(
            FilePickerCopy::class,
            CopyPickedFileListener::class
        );
    }
}