# Save the contents of Apple Notes

## Usage

The build creates a zip file that you can extract somewhere else and run
using the included `savenotes` script (on Unix systems) or just
`java -ea -jar savenotes-<version>.jar args ...`.

By default, it saves all your notes to the current directory, in
subdirectories based on your Notes folders.  Use the `-d dir`
option to tell it where to save.

Usage: savenotes [-f db] [-a] [-v] [-d dir] [-t pattern] [-h] [-r] [-m] [-p] [-k] [-X]

Options are:

* `-f` *db* - read a database file other than the default
* `-a` - save all notes (including deleted ones?)
* `-v` - verbose output
* `-d` *dir* - save to the specified directory instead of the current directory
* `-t` *pattern* - only save notes whose title matches the pattern regexp
* `-h` - save in html format
* `-r` - save in raw (archived object) format
* `-m` - save in markdown format
* `-p` - print to stdout instead of saving to a file
* `-k` - save in marked text format (mostly useful for debugging with -X)
* `-X` - display lots of detailed debugging output

## Data format

[NSAttributedString.txt](NSAttributedString.txt) describes the
format of the note data, which is stored as a (non-keyed) Objective-C
archived object, gzipped, in a sqlite database.
The `savenotes` program reads the data from the database, interprets
the raw note data format, and saves the contents of the notes.
