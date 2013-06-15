package org.mackler.metronome

import scala.concurrent.duration._

class MainActor extends Actor {

  var uiOption: Option[MainActivity] = None
  var mediaPlayerOption: Option[MediaPlayer] = None

  var tempo = 0

  override def preStart {
    tempo = 60
  }

  override def postStop {
    mediaPlayerOption.get.release()
    mediaPlayerOption = None
  }

  def receive = {
    case Start(androidContext) ⇒
      logD(s"Starting")
      mediaPlayerOption.get.prepare()
      mediaPlayerOption.get.seekTo(0)
      mediaPlayerOption.get.start()

    case SetUi(activity) ⇒
      uiOption = Option(activity)
      mediaPlayerOption = Option(new MediaPlayer())

      val resources: android.content.res.Resources = uiOption.get.getResources
      val afd: android.content.res.AssetFileDescriptor = resources.openRawResourceFd(R.raw.click)
      mediaPlayerOption.get.setDataSource(afd.getFileDescriptor, afd.getStartOffset, afd.getLength)
      afd.close
      mediaPlayerOption.get.setLooping(true)

    case SetTempo(bpm) ⇒
      tempo = bpm
      uiOption.get.runOnUiThread(new Runnable { def run {
	uiOption.get.displayTempo(tempo)
      }})

    case Stop ⇒
      logD("Main Actor received Stop message")
      mediaPlayerOption.get.stop()
  }
}
