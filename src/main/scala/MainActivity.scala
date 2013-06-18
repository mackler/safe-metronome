package org.mackler.metronome

class MainActivity extends Activity with TypedActivity {
  private val mIsPlaying = false

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.main_activity, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.preferences ⇒
        chooseSound()
        true
      case R.id.about ⇒
        showAbout()
        true
      case _ ⇒ super.onOptionsItemSelected(item)
    }
  }

  private def showAbout() {
    logD("show about menu item clicked")
  }

  def setSeek(progress: Int) {
    findView(TR.seek_bar).setProgress(progress)
  }

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)

    val onSeekBarChangeListener = new OnSeekBarChangeListener {
      def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
	if (fromUser) mainActor ! SetTempo(progress + 32)
      }
      def onStartTrackingTouch(seekBar: SeekBar) {}
      def onStopTrackingTouch(seekBar: SeekBar) {}
    }

    findView(TR.seek_bar).setOnSeekBarChangeListener(onSeekBarChangeListener)

    mainActor ! SetUi(this)
  }

  def toggle(view: View) {
    val startString = getString(R.string.start)
    val stopString = getString(R.string.stop)
    findView(TR.control_button).getText match {
      case `startString` ⇒ mainActor ! Start
      case `stopString` ⇒ mainActor ! Stop
    }
  }

  def displayStartButton { displayButton(true) }
  def displayStopButton { displayButton(false) }

  private def displayButton(start: Boolean) {
    val button = findView(TR.control_button)
    start match {
      case true ⇒
        button.setText(R.string.start)
        button.setBackgroundResource(android.R.color.holo_green_light)

      case false ⇒
        button.setText(R.string.stop)
        button.setBackgroundResource(android.R.color.holo_red_dark)
    }
  }

  def faster(view: View) {
    mainActor ! Increase
  }

  def slower(view: View) {
    mainActor ! Decrease
  }

  def displayTempo(tempo: Int) {
    findView(TR.tempo).setText(tempo.toString)
    findView(TR.marking).setText(marking(tempo))
  }

  private def marking(tempo: Int): String = {
    if (tempo < 40) "Grave"
    else if (tempo < 45) "Lento"
    else if (tempo < 50) "Largo"
    else if (tempo < 55) "Larghetto"
    else if (tempo < 64) "Adagio"
    else if (tempo < 69) "Adagietto"
    else if (tempo < 73) "Andante moderato"
    else if (tempo < 78) "Andante"
    else if (tempo < 83) "Andantino"
    else if (tempo < 86) "Marcia moderato"
    else if (tempo < 98) "Moderato"
    else if (tempo < 109) "Allegretto"
    else if (tempo < 132) "Allegro"
    else if (tempo < 140) "Vivace"
    else if (tempo < 150) "Allegrissimo"
    else if (tempo < 178) "Presto"
    else "Prestissimo"
  }

  private def chooseSound() {
    (new SoundFragment).show(getFragmentManager.beginTransaction(),"soundChooser")
  }

  class SoundFragment extends DialogFragment {
   override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
     val builder = new AlertDialogBuilder(getActivity())
     builder.setTitle(R.string.choose_sound).
     setItems(R.array.sound_names, new DialogOnClickListener() {
       def onClick(dialog: DialogInterface, which: Int) {
	 mainActor ! SetSound(which)
       }
     }).
     create()
   }
  }

}
