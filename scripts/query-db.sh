#! /bin/bash
adb pull /sdcard/neurhome /tmp
sqlite3 /tmp/neurhome/application_log.db 'select package,timestamp from application_log;'
