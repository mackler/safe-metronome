package org.mackler {
  package object metronome {

    type Actor       = akka.actor.Actor
    type ActorRef    = akka.actor.ActorRef
    val  ActorSystem = akka.actor.ActorSystem
    type Cancellable = akka.actor.Cancellable
    val  Props       = akka.actor.Props
    type MediaPlayer = android.media.MediaPlayer
    def MediaPlayerCreate(context: android.content.Context, resid: Int) =
      android.media.MediaPlayer.create(context, resid)

    type OnSeekBarChangeListener = android.widget.SeekBar.OnSeekBarChangeListener
    type SeekBar = android.widget.SeekBar

    type AlertDialogBuilder = android.app.AlertDialog.Builder
    type Activity          = android.app.Activity
    type Dialog            = android.app.Dialog
    type DialogFragment    = android.app.DialogFragment
    type Resources         = android.content.res.Resources
    type Bundle            = android.os.Bundle
    def  logD(msg: String) = android.util.Log.d("SafeMetronome", msg)
    type View              = android.view.View
    type Menu              = android.view.Menu
    type MenuItem          = android.view.MenuItem
    type DialogOnClickListener = android.content.DialogInterface.OnClickListener
    type DialogInterface   = android.content.DialogInterface

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

  }
}
