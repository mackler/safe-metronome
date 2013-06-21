package org.mackler {
  package object metronome {

    type Actor       = akka.actor.Actor
    type ActorRef    = akka.actor.ActorRef
    val  ActorSystem = akka.actor.ActorSystem
    type Cancellable = akka.actor.Cancellable
    val  Props       = akka.actor.Props

    type AlertDialogBuilder         = android.app.AlertDialog.Builder
    type Activity                   = android.app.Activity
    type Dialog                     = android.app.Dialog
    type DialogFragment             = android.app.DialogFragment
    type Resources                  = android.content.res.Resources
    val  MODE_PRIVATE               = android.content.Context.MODE_PRIVATE
    type DialogInterface            = android.content.DialogInterface
    type DialogOnClickListener      = android.content.DialogInterface.OnClickListener
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
    def  logD(msg: String)          = android.util.Log.d("SafeMetronome", msg)
    type Menu                       = android.view.Menu
    type MenuItem                   = android.view.MenuItem
    type View                       = android.view.View
    type SeekBar                    = android.widget.SeekBar
    type OnSeekBarChangeListener    = android.widget.SeekBar.OnSeekBarChangeListener

    lazy val actorSystem = ActorSystem("ActorSystem")

    lazy val mainActor: akka.actor.ActorRef =
      actorSystem.actorOf(Props[MainActor], name = "MainActor")

    case object Start
    case object Stop
    case object Decrease
    case object Increase
    case class SetTempo(bpm: Int)
    case class SetSound(sound: Int)
    case class SetUi(activity: MainActivity)
    case class SavePreferences(preferences: SharedPreferences)

  }
}
