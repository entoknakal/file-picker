<?php

namespace Andryzulfikar\FilePicker;


class FilePicker
{
    /**
     * Membuka Document/File Picker Native Android
     * 
     * @param array $options Contoh: ['types' => ['*']] // Pilih semua jenis file
     * @example '$types' jenis file yang bisa dimasukkan: *, images, pdf, zip, word, excel, videos, audios
     * @return mixed
     */
    public function open(array $options = []): mixed
    {
        // Mengecek apakah fungsi helper nativephp_call tersedia di runtime NativePHP 4
        if (function_exists('nativephp_call')) {
            // Memanggil Bridge Function 'FilePicker.OpenPicker' dengan opsi yang di-encode ke JSON
            $result = nativephp_call('FilePicker.OpenPicker', json_encode($options));

            // Decode respons JSON menjadi Array associative PHP
            $decoded = json_decode($result, true);

            if (!is_array($decoded)) {
                return null;
            }

            // Utamakan array 'files', lalu 'data', atau kembalikan array $decoded langsung
            return $decoded['files'] ?? $decoded['data'] ?? $decoded;
        }

        return null;
    }
}