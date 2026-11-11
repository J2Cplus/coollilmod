"""Inventory and enforce packaging rules. This is not an antivirus engine or a safety certification."""
import hashlib, io, json, pathlib, sys, zipfile
artifact=pathlib.Path(sys.argv[1]);forbidden={'.exe','.dll','.so','.dylib','.msi','.com','.scr','.ps1','.cmd','.bat','.mjs','.js','.jjs','.py','.sh','.vbs','.vbe','.wsf','.hta'}
entries=[];own_classes=0;total=0

def inspect(data,prefix='',depth=0):
 global own_classes,total
 assert depth<=8,'Archive nesting limit'
 with zipfile.ZipFile(io.BytesIO(data)) as archive:
  for entry in archive.infolist():
   if entry.is_dir():continue
   assert entry.file_size<=64*1024*1024,'Oversized archive entry'
   content=archive.read(entry);total+=len(content);assert total<=256*1024*1024,'Expansion limit';name=prefix+entry.filename
   assert pathlib.PurePosixPath(entry.filename).suffix.lower() not in forbidden,f'Forbidden runtime payload: {name}'
   assert not content.startswith(b'MZ'),f'Embedded PE payload: {name}'
   entries.append({'path':name,'size':len(content),'sha256':hashlib.sha256(content).hexdigest()})
   if not prefix and entry.filename.startswith('dev/luna/') and entry.filename.endswith('.class'):
    own_classes+=1
    for symbol in [b'java/lang/ProcessBuilder',b'java/lang/Runtime',b'javax/script',b'ExecutionPolicy',b'powershell.exe',b'node.exe']:
     assert symbol not in content,f'Forbidden process/script API in {name}: {symbol!r}'
   if entry.filename.endswith('.jar'):inspect(content,name+'!/',depth+1)
inspect(artifact.read_bytes());assert own_classes>=10,'Application classes missing'
report={'artifact':artifact.name,'sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),'size':artifact.stat().st_size,'archiveEntries':len(entries),'applicationClasses':own_classes,'uncompressedBytes':total,'checks':'No executable/script payloads, no PE files, no subprocess or script-engine references in application bytecode','antivirusClearance':False,'entries':entries}
print(json.dumps(report,indent=2))
