<?php

namespace Andryzulfikar\FilePicker\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static mixed execute(array $options = [])
 * @method static object|null getStatus()
 *
 * @see \Andryzulfikar\FilePicker\FilePicker
 */
class FilePicker extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \Andryzulfikar\FilePicker\FilePicker::class;
    }
}