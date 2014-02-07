package org.mackler {
  package object metronome {

    final val DEVELOPMENT_MODE = false

    /* Contents:
     *
     * Akka includes
     * Android includes
     * Scala includes
     * Java includes
     * Akka messages
     * Global variables and functions
     */

    /* Akka includes */

    type Actor       = akka.actor.Actor
    type ActorRef    = akka.actor.ActorRef
    val  ActorSystem = akka.actor.ActorSystem
    type Cancellable = akka.actor.Cancellable
    val  Props       = akka.actor.Props
    type RequiresMessageQueue[T]        = akka.dispatch.RequiresMessageQueue[T]
    type UnboundedMessageQueueSemantics = akka.dispatch.UnboundedMessageQueueSemantics

    /* Android includes */

    type AlertDialogBuilder         = android.app.AlertDialog.Builder
    type Activity                   = android.app.Activity
    type Dialog                     = android.app.Dialog
    type DialogFragment             = android.app.DialogFragment
    type Resources                  = android.content.res.Resources
    val  MODE_PRIVATE               = android.content.Context.MODE_PRIVATE
    type DialogInterface            = android.content.DialogInterface
    type DialogOnClickListener      = android.content.DialogInterface.OnClickListener
    type Intent                     = android.content.Intent
    val  ACTION_SEND                = android.content.Intent.ACTION_SEND
    val  ACTION_VIEW                = android.content.Intent.ACTION_VIEW
    val  EXTRA_EMAIL                = android.content.Intent.EXTRA_EMAIL
    val  EXTRA_SUBJECT              = android.content.Intent.EXTRA_SUBJECT
    type SharedPreferences          = android.content.SharedPreferences
    val  CHANNEL_OUT_MONO           = android.media.AudioFormat.CHANNEL_OUT_MONO
    val  ENCODING_PCM_16BIT         = android.media.AudioFormat.ENCODING_PCM_16BIT
    type AudioTrack                 = android.media.AudioTrack
    val  MODE_STREAM                = android.media.AudioTrack.MODE_STREAM
    val  PLAYSTATE_PAUSED           = android.media.AudioTrack.PLAYSTATE_PAUSED
    val  PLAYSTATE_PLAYING          = android.media.AudioTrack.PLAYSTATE_PLAYING
    val  PLAYSTATE_STOPPED          = android.media.AudioTrack.PLAYSTATE_STOPPED
    val  audioTrackGetMinBufferSize = android.media.AudioTrack.getMinBufferSize _
    type OnPlaybackPositionUpdateListener =
      android.media.AudioTrack.OnPlaybackPositionUpdateListener 
    type Bundle                     = android.os.Bundle
    def  logD(msg: String)          = if (DEVELOPMENT_MODE) android.util.Log.d("SafeMetronome", msg)
    type LayoutInflater             = android.view.LayoutInflater
    type Menu                       = android.view.Menu
    type MenuItem                   = android.view.MenuItem
    val  ACTION_DOWN                = android.view.MotionEvent.ACTION_DOWN
    val  FLAG_KEEP_SCREEN_ON        = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    type View                       = android.view.View
    val  GONE                       = android.view.View.GONE
    val  INVISIBLE                  = android.view.View.INVISIBLE
    val  VISIBLE                    = android.view.View.VISIBLE
    type OnLongClickListener        = android.view.View.OnLongClickListener
    type OnTouchListener            = android.view.View.OnTouchListener
    type ViewGroup                  = android.view.ViewGroup
    type Button                     = android.widget.Button
    type NumberPicker               = android.widget.NumberPicker
    type OnValueChangeListener      = android.widget.NumberPicker.OnValueChangeListener
    type SeekBar                    = android.widget.SeekBar
    type OnSeekBarChangeListener    = android.widget.SeekBar.OnSeekBarChangeListener

    /* Scala includes */

    type ExecutionContext = scala.concurrent.ExecutionContext

    /* Java includes */

    type Pattern = java.util.regex.Pattern

    /* Akka Messages */
    case class  SetUi(activity: MainActivity)
    case class  Start(timestamp: Long)
    case class  Stop(timestamp: Long)
    case object PlayLoop
    case object SingleTap
    case class  SetTempo(bpm: Float, timestamp: Long)
    case class  SetSound(sound: Int)
    case class  SetAccent(beats: Int)
    case class  SavePreferences(preferences: SharedPreferences)

    case class  BuildChops(startTempo: Float, timeInMinutes: Int)
    case object Tick
    case object IncrementCountdown
    case object DecrementCountdown
    case object PauseChopsBuilder
    case object ChopsComplete
    case object ChopsCancel
    case object UnloadAlert

    /* Global variables and functions */

    def patternCompile(regex: String) = java.util.regex.Pattern.compile(regex)

    lazy val actorSystem = {
      logD("Starting Akka ActorSystem")
      val startTime = System.currentTimeMillis
      val actorSystem = ActorSystem("ActorSystem")
      logD(s"ActorSystem startup took ${System.currentTimeMillis - startTime} milliseconds")
      actorSystem
    }

    lazy val mainActor: akka.actor.ActorRef =
      actorSystem.actorOf(Props[MainActor], name = "MainActor")

    /** @arg resources The android Resources object to use to access the raw sound
     *  @arg source Resource id of the raw sound file
     * @arg dest The Array into which to copy the sound data
     * @arg size The number of sound samples to read */
    def readRawSound(resources: Resources, source: Int, dest: Array[Short], size: Int) {
      val dataInputStream = new java.io.DataInputStream(resources.openRawResource(source))
      (0 to size-1) foreach {
	dest.update(_, dataInputStream.readShort())
      }
      dataInputStream.close()
    }


  }
}
