#!/bin/sh
# Compila autoclicker.exe (Windows x64) con MinGW-w64.
# Da Linux: sudo apt-get install mingw-w64 && ./build.sh
# Da Windows (MSYS2/MinGW): CC=gcc WINDRES=windres ./build.sh
set -e
cd "$(dirname "$0")"
CC=${CC:-x86_64-w64-mingw32-gcc}
WINDRES=${WINDRES:-x86_64-w64-mingw32-windres}

"$WINDRES" autoclicker.rc -O coff -o autoclicker_res.o
"$CC" -O2 -Wall -Wextra -municode -mwindows -static -s \
    -o autoclicker.exe autoclicker.c autoclicker_res.o \
    -lcomctl32 -lwinmm
rm -f autoclicker_res.o
echo "Creato $(pwd)/autoclicker.exe"
