package com.example.poseoverlay

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

import android.provider.MediaStore
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Joint(val name:String,val x:Float,val y:Float)
data class PoseSnapshot(val joints:List<Joint>)
private val jointNames=listOf("nose","leftShoulder","rightShoulder","leftElbow","rightElbow","leftWrist","rightWrist","leftHip","rightHip","leftKnee","rightKnee","leftAnkle","rightAnkle")
private val pairs=listOf(
    "leftShoulder" to "rightShoulder","leftShoulder" to "leftElbow","leftElbow" to "leftWrist",
    "rightShoulder" to "rightElbow","rightElbow" to "rightWrist","leftShoulder" to "leftHip",
    "rightShoulder" to "rightHip","leftHip" to "rightHip","leftHip" to "leftKnee",
    "leftKnee" to "leftAnkle","rightHip" to "rightKnee","rightKnee" to "rightAnkle"
)


private data class SavedPose(val name: String, val uri: String)

private class PoseLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("pose_library", Context.MODE_PRIVATE)
    private val key = "poses"

    fun load(): List<SavedPose> {
        val arr = JSONArray(prefs.getString(key, "[]"))
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(SavedPose(o.optString("name"), o.optString("uri")))
            }
        }
    }

    fun save(name: String, uri: String) {
        val arr = JSONArray()
        load().filterNot { it.uri == uri }.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("uri", it.uri)
            })
        }
        arr.put(JSONObject().apply {
            put("name", name)
            put("uri", uri)
        })
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun delete(uri: String) {
        val arr = JSONArray()
        load().filterNot { it.uri == uri }.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("uri", it.uri)
            })
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        setContent{VideoPoseCamera()}}
}

@Composable
fun VideoPoseCamera(){
    val context=LocalContext.current
    var cameraOk by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    var audioOk by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)}
    val camReq=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){cameraOk=it}
    val audioReq=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){audioOk=it}
    if(!cameraOk){
        Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
            Button({camReq.launch(Manifest.permission.CAMERA)}){Text("السماح بالكاميرا")}
        };return
    }

    var reference by remember{mutableStateOf<Bitmap?>(null)}
    var refPose by remember{mutableStateOf<PoseSnapshot?>(null)}
    var livePose by remember{mutableStateOf<PoseSnapshot?>(null)}
    var match by remember{mutableIntStateOf(0)}
    var tracking by remember{mutableStateOf(true)}
    var skeleton by remember{mutableStateOf(true)}
    var recording by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("اختار Pose باش يبدأ التتبع")}
    var opacity by remember{mutableFloatStateOf(.35f)}
    var scale by remember{mutableFloatStateOf(1f)}
    var tx by remember{mutableFloatStateOf(0f)}
    var ty by remember{mutableFloatStateOf(0f)}
    var locked by remember{mutableStateOf(false)}
    var videoCapture by remember{mutableStateOf<VideoCapture<Recorder>?>(null)}
    var recordingObj by remember{mutableStateOf<Recording?>(null)}

    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        uri?:return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use{reference=BitmapFactory.decodeStream(it)}
        reference?.let{detectPose(context,it){p->refPose=p;message=if(p!=null)"✓ Pose جاهزة للتتبع" else "ما تلقيناش الجسم فالصورة"}}
        scale=1f;tx=0f;ty=0f;locked=false
    }

    val preview=remember{PreviewView(context)}
    DisposableEffect(Unit){
        val executor=Executors.newSingleThreadExecutor()
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider=future.get()
            val prev=Preview.Builder().build().also{it.setSurfaceProvider(preview.surfaceProvider)}
            val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            val recorder=Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
            val vc=VideoCapture.withOutput(recorder)
            videoCapture=vc
            val detector=PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
            analysis.setAnalyzer(executor){proxy->
                val img=proxy.image
                if(!tracking||refPose==null||img==null){proxy.close();return@setAnalyzer}
                detector.process(InputImage.fromMediaImage(img,proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener{p->livePose=p.toSnapshot();match=poseMatch(refPose,livePose)}
                    .addOnCompleteListener{proxy.close()}
            }
            try{
                provider.unbindAll()
                provider.bindToLifecycle(context as ComponentActivity,CameraSelector.DEFAULT_BACK_CAMERA,prev,analysis,vc)
            }catch(_:Exception){}
        },ContextCompat.getMainExecutor(context))
        onDispose{executor.shutdown();}
    }

    fun startStopVideo(){
        val vc=videoCapture?:return
        if(recording){
            recordingObj?.stop();recordingObj=null;recording=false;message="✓ الفيديو تحفظ فـGallery";return
        }
        if(!audioOk){audioReq.launch(Manifest.permission.RECORD_AUDIO);return}
        val name="PoseVideo_${System.currentTimeMillis()}.mp4"
        val values=ContentValues().apply{
            put(MediaStore.Video.Media.DISPLAY_NAME,name)
            put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/PoseCamera")
        }
        val output=MediaStoreOutputOptions.Builder(context.contentResolver,MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        val pending=vc.output.prepareRecording(context,output).withAudioEnabled()
        recordingObj=pending.start(ContextCompat.getMainExecutor(context)){event->
            when(event){
                is VideoRecordEvent.Start->{recording=true;message="● التسجيل خدام — Pose Tracking مستمر"}
                is VideoRecordEvent.Finalize->{recording=false;message=if(event.hasError())"وقع مشكل فالفيديو" else "✓ الفيديو تحفظ فـGallery"}
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)){
        AndroidView({preview},Modifier.fillMaxSize())

        reference?.let{bmp->
            Image(bmp.asImageBitmap(),null,Modifier.align(Alignment.Center).fillMaxWidth()
                .graphicsLayer(alpha=opacity,scaleX=scale,scaleY=scale,translationX=tx,translationY=ty)
                .pointerInput(locked){if(!locked)detectTransformGestures{_,pan,zoom,_->{tx+=pan.x;ty+=pan.y;scale=(scale*zoom).coerceIn(.25f,4f)}}})
        }
        if(skeleton&&livePose!=null) SkeletonOverlay(livePose!!,Modifier.fillMaxSize())

        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
            if(refPose!=null)Surface(color=Color.Black.copy(.78f),shape=RoundedCornerShape(18.dp)){
                Text("POSE MATCH  $match%",color=Color.White,fontSize=18.sp,modifier=Modifier.padding(horizontal=18.dp,vertical=9.dp))
            }
        }

        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp)){
            Surface(color=Color.Black.copy(.87f),shape=RoundedCornerShape(18.dp)){
                Column(Modifier.padding(9.dp)){
                    Text(message,color=Color.White,fontSize=11.sp)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        Button({picker.launch("image/*")}){Text("🖼 Pose")}
                        Button({tracking=!tracking}){Text(if(tracking)"⏸ Track" else "▶ Track")}
                        Button({skeleton=!skeleton}){Text(if(skeleton)"Skeleton" else "Clean")}
                        Button({locked=!locked}){Text(if(locked)"🔒" else "🔓")}
                        Button({startStopVideo()},colors=ButtonDefaults.buttonColors(containerColor=if(recording)Color.Red else MaterialTheme.colorScheme.primary)){
                            Text(if(recording)"■ Stop" else "● Video")
                        }
                    }
                    Text("Overlay ${(opacity*100).roundToInt()}%",color=Color.White,fontSize=10.sp)
                    Slider(opacity,{opacity=it},valueRange=.05f..1f)
                }
            }
        }
    }
}

@Composable
private fun SkeletonOverlay(pose:PoseSnapshot,modifier:Modifier){
    androidx.compose.foundation.Canvas(modifier){
        fun pt(n:String)=pose.joints.firstOrNull{it.name==n}?.let{androidx.compose.ui.geometry.Offset(it.x*size.width,it.y*size.height)}
        pairs.forEach{(a,b)->val p1=pt(a);val p2=pt(b);if(p1!=null&&p2!=null)drawLine(Color.White,p1,p2,5f)}
        pose.joints.forEach{drawCircle(Color.White,6f,androidx.compose.ui.geometry.Offset(it.x*size.width,it.y*size.height))}
    }
}

private fun Pose.toSnapshot():PoseSnapshot{
    fun add(type:Int,name:String,list:MutableList<Joint>){getPoseLandmark(type)?.let{list.add(Joint(name,it.position.x,it.position.y))}}
    val l=mutableListOf<Joint>()
    add(com.google.mlkit.vision.pose.PoseLandmark.NOSE,"nose",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER,"leftShoulder",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER,"rightShoulder",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.LEFT_ELBOW,"leftElbow",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_ELBOW,"rightElbow",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST,"leftWrist",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST,"rightWrist",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.LEFT_HIP,"leftHip",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_HIP,"rightHip",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.LEFT_KNEE,"leftKnee",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_KNEE,"rightKnee",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.LEFT_ANKLE,"leftAnkle",l)
    add(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_ANKLE,"rightAnkle",l)
    return PoseSnapshot(l)
}
private fun detectPose(context:Context,bmp:Bitmap,done:(PoseSnapshot?)->Unit){
    val d=PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build())
    d.process(InputImage.fromBitmap(bmp,0)).addOnSuccessListener{p->done(p.toSnapshot().takeIf{it.joints.size>=5})}.addOnFailureListener{done(null)}
}
private fun poseMatch(ref:PoseSnapshot?,live:PoseSnapshot?):Int{
    if(ref==null||live==null)return 0
    var total=0f;var n=0
    jointNames.forEach{name->
        val a=ref.joints.firstOrNull{it.name==name};val b=live.joints.firstOrNull{it.name==name}
        if(a!=null&&b!=null){val d=sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y));total+=(1f-min(1f,d*3.2f));n++}
    }
    return if(n==0)0 else (total/n*100f).roundToInt().coerceIn(0,100)
}


@Composable
private fun PoseLibraryPanel(
    poses: List<SavedPose>,
    onUse: (SavedPose) -> Unit,
    onDelete: (SavedPose) -> Unit,
    onClose: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saved Poses", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onClose) { Text("Close") }
            }
            Spacer(Modifier.height(16.dp))
            if (poses.isEmpty()) {
                Text("No saved poses yet.", color = Color.White.copy(alpha = .8f))
            } else {
                poses.forEach { pose ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            pose.name,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onUse(pose) }) { Text("Use") }
                        TextButton(onClick = { onDelete(pose) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
