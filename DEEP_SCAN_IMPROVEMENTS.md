# Deep Scan Improvements

## Overview
The deep scan has been significantly enhanced to provide much more comprehensive file recovery capabilities compared to the quick scan.

## What Makes Deep Scan Better Than Quick Scan

### Quick Scan (Basic)
- Only scans MediaStore database
- Finds files that are indexed by the system
- Fast but limited coverage
- Misses hidden, deleted, and unindexed files

### Deep Scan (Enhanced)
The deep scan now includes **5 comprehensive scanning phases**:

## 1. Regular MediaStore Scan
- Same as quick scan (baseline coverage)
- Scans all indexed images, videos, and documents

## 2. Hidden Files Detection ✨
**NEW: Scans for hidden files in:**
- System hidden directories (`.hidden`, `.trash`, `.recycle`, `.thumbnails`)
- App-specific hidden folders:
  - `.WhatsApp`
  - `.Telegram`
  - `.Instagram`
  - `.Facebook`
  - `.Snapchat`
  - `.TikTok`
- Files starting with "." (dot files)
- Directories with `.nomedia` files (hidden from gallery apps)
- Camera and picture thumbnail caches

## 3. Trash Bin & Recycle Bin Scanning 🗑️
**NEW: Comprehensive trash directory scanning:**

### System Trash Locations
- `/storage/emulated/0/.Trash`
- `/storage/emulated/0/Trash`
- `/storage/emulated/0/RECYCLE.BIN`
- `/storage/emulated/0/.Trash-1000` (user-specific trash)

### App Trash Locations
- **File Managers**: ES File Explorer, File Manager, ASUS FileManager, MiDrive
- **Gallery Apps**: DCIM/Trash, Pictures/Trash, Pictures/.trashed
- **Cloud Storage**: Google Drive, Dropbox local trash
- **Social Media**: WhatsApp, Telegram, Instagram trash folders
- **MediaStore**: Android system media provider trash cache

### Smart Detection
- Automatically finds any directory with "trash", "deleted", "recycle", or "bin" in the name
- Scans all subdirectories recursively

## 4. Deleted Files Recovery 🔄
**NEW: Multiple approaches to find deleted files:**

### Orphaned Files Detection
- Finds files still in MediaStore database but physically deleted
- Scans last 1000 modified files for orphaned entries
- Useful for recently deleted files not yet purged from database

### Temporary & Pending Directories
- `/Android/data/com.android.providers.media/files/.pending`
- `/Android/data/com.android.providers.media/files/.deleted`
- Temporary directories (`.temp`, `tmp`, `.tmp`)
- App cache directories (Gallery, Google Photos)

### Backup Directories
- `.backup`
- `backup`
- `Backups`
- `Android/data/backup`

### Android 11+ Features
- Uses MediaStore deleted files API (if available)
- Scans files modified in last 30 days for deletion markers

## 5. Unindexed Files Discovery 📁
**NEW: Scans for files not in MediaStore:**

### Standard Directories
- Download/Downloads
- Documents
- Music, Movies, Pictures
- DCIM (Camera, Screenshots)

### Messaging Apps
- WhatsApp Media (Images, Videos, Documents)
- Telegram (Images, Videos, Documents)
- Instagram, Snapchat, TikTok
- Facebook, Twitter

### Other Sources
- Bluetooth transfers
- Voice/Call Recordings
- App data directories (`Android/data`, `Android/media`)
- SD card paths (`/storage/sdcard1`, `/mnt/extSdCard`)
- Custom user folders (Media, Files, MyFiles)

### Root Storage Scan
- Scans root storage for unindexed files
- Automatically detects custom media directories
- Finds files in unusual locations

## Technical Improvements

### Duplicate Prevention
- All found files are deduplicated by URI
- Prevents showing the same file multiple times from different scan phases

### Smart Filtering
- All scan phases respect user filters (file type, size, date range)
- Consistent filtering across all scan methods

### Performance Optimization
- Recursive directory scanning with error handling
- Graceful handling of permission issues
- Extensive logging for debugging

### File Type Detection
- MIME type detection from file extensions
- Proper MediaKind classification (IMAGE, VIDEO, DOCUMENT, AUDIO, OTHER)
- Support for common formats: jpg, png, gif, mp4, avi, pdf, doc, xls, etc.

## Expected Results

### Files the Deep Scan Can Now Find:
✅ Hidden photos/videos in app folders  
✅ Files in trash bins (not yet permanently deleted)  
✅ Recently deleted files still in temp directories  
✅ Orphaned database entries  
✅ Unindexed files from downloads or transfers  
✅ Files in `.nomedia` directories  
✅ Cache and backup copies  
✅ Files on external SD cards  
✅ Social media app cached media  
✅ Bluetooth transferred files  

### What Deep Scan Still Cannot Find:
❌ Permanently deleted files (overwritten data)  
❌ Files in encrypted app containers without root  
❌ Files in system partitions without root access  
❌ Corrupted files with no valid headers  

## Permissions Required

For best results, the deep scan needs:
- `READ_EXTERNAL_STORAGE` (Android 10 and below)
- `MANAGE_EXTERNAL_STORAGE` (Android 11+) - for full file system access
- Without these permissions, some directories may not be accessible

## Logging

All scan operations are logged with tags:
- "MediaRepository" - Main scanning operations
- Each scan phase logs how many files it found
- Inaccessible directories are logged for debugging

## Conclusion

The enhanced deep scan is now **significantly more powerful** than the quick scan, with the ability to find:
- Hidden files in dozens of locations
- Trash bin files from system and apps
- Recently deleted files
- Orphaned database entries
- Unindexed files across the entire storage

This makes it a true "recovery" tool rather than just a file browser.

