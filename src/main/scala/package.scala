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


    type Activity          = android.app.Activity
    type Bundle            = android.os.Bundle
    def  logD(msg: String) = android.util.Log.d("SafeMetronome", msg)
    type View              = android.view.View

    lazy val actorSystem = ActorSystem("ActorSystem")

    lazy val mainActor: akka.actor.ActorRef =
      actorSystem.actorOf(Props[MainActor], name = "MainActor")

    case class Start(activity: android.app.Activity)
    case object Stop
    case class SetTempo(bpm: Int)
    case class SetUi(activity: MainActivity)
  }
}
