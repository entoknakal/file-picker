<?php

namespace entoknakal\FilePicker\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static mixed execute(array $options = [])
 * @method static object|null getStatus()
 *
 * @see \entoknakal\FilePicker\FilePicker
 */
class FilePicker extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \entoknakal\FilePicker\FilePicker::class;
    }
}