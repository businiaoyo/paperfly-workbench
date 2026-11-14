import {readFile,stat} from 'node:fs/promises'
import {createReadStream} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {createHash} from 'node:crypto'
import path from 'node:path'
import os from 'node:os'

// Publishing stays on the developer's machine. Never put these credentials in the APK.
const config=process.env.PAPERFLY_UPDATE_CONFIG || path.join(os.homedir(),'Desktop/AI工具/包分发/.env.deploy.local')
if(!process.env.PUBLISH_TOKEN)process.loadEnvFile(config)
const base=(process.env.UPDATE_CENTER_URL || process.env.NAS_APP_URL || '').replace(/\/$/,'')
const origin=new URL(base)
if(!['http:','https:'].includes(origin.protocol)||origin.username||origin.password||origin.search||origin.hash||!process.env.PUBLISH_TOKEN)throw Error('Invalid update-center configuration')
const [apkArgument,notesArgument,mode]=process.argv.slice(2)
if(!apkArgument||!notesArgument||!['--draft-only',undefined].includes(mode))throw Error('Usage: node scripts/publish-update.mjs APP.apk NOTES.txt [--draft-only]')
const apk=path.resolve(apkArgument),name=path.basename(apk),notes=await readFile(notesArgument,'utf8')
const sdk=process.env.ANDROID_HOME || path.join(os.homedir(),'Library/Android/sdk')
const buildTools=process.env.ANDROID_BUILD_TOOLS || path.join(sdk,'build-tools/34.0.0')
execFileSync(path.join(buildTools,'apksigner'),['verify',apk],{stdio:['ignore','pipe','pipe']})
const badging=execFileSync(path.join(buildTools,'aapt'),['dump','badging',apk],{encoding:'utf8'})
const identity=/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/m.exec(badging)
if(!identity||identity[1]!=='cn.paperfly.workbench'||!/^\d+\.\d+\.\d+$/.test(identity[3]))throw Error('Expected a signed stable OpenWorkbench APK')
const version=identity[3],versionCode=Number(identity[2]),size=(await stat(apk)).size
const hash=createHash('sha256');for await(const chunk of createReadStream(apk))hash.update(chunk);const sha256=hash.digest('hex')
async function api(route,options={}){
 const response=await fetch(base+route,{...options,headers:{Authorization:`Bearer ${process.env.PUBLISH_TOKEN}`,...options.headers},signal:AbortSignal.timeout(120000)})
 const value=await response.json();if(!response.ok)throw Error(`Update center ${response.status}: ${value.error?.message||'request failed'}`);return value
}
const before=await api('/api/admin/releases?appId=open-workbench&channel=stable')
const published=before.releases.find(r=>r.version===version&&r.status==='published')
if(published){
 if(published.versionCode!==versionCode||!published.artifacts.some(a=>a.name===name&&a.sha256===sha256&&a.size===size))throw Error('Published version already exists with different contents')
 console.log(JSON.stringify({alreadyPublished:true,version,versionCode,releaseId:published.id,sha256},null,2));process.exit(0)
}
const {release}=await api('/api/admin/releases',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({appId:'open-workbench',channel:'stable',version,versionCode,notes,mandatory:false})})
const {artifact}=await api(`/api/admin/releases/${release.id}/artifacts/${encodeURIComponent(name)}?platform=android&arch=universal&kind=apk`,{method:'PUT',headers:{'Content-Type':'application/vnd.android.package-archive'},body:createReadStream(apk),duplex:'half'})
if(artifact.sha256!==sha256||artifact.size!==size)throw Error('Uploaded artifact does not match local APK; draft retained')
if(mode!=='--draft-only')await api(`/api/admin/releases/${release.id}/publish`,{method:'POST'})
console.log(JSON.stringify({published:mode!=='--draft-only',version,versionCode,releaseId:release.id,url:artifact.url,sha256,size},null,2))
