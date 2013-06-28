package org.mackler.metronome

class StartingTempoDialog extends DialogFragment {
  var mMinutes: Int = 0
  var mStartTempo: Int = 0

  def minutes = mMinutes

  override def onCreate (savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    mMinutes = getArguments.getInt("minutes")
    mStartTempo = getArguments.getInt("startTempo")
  }

  override def onCreateView (
    inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle
  ): View = {
    logD(s"StartingTempoDialog's LayoutInflater context is ${inflater.getContext}, a ${inflater.getContext.getClass.getName}")
    this.getDialog.setTitle(getString(R.string.set_start_tempo))
    val tempoLayout: View = inflater.inflate(R.layout.start_tempo, container, false)
    val tempoPicker = tempoLayout.findViewById(R.id.tempo_picker).asInstanceOf[android.widget.NumberPicker]
    tempoPicker.setMinValue(32)
    tempoPicker.setMaxValue(MainActor.MAX_TEMPO)
    tempoPicker.setValue(mStartTempo)
    tempoPicker.setWrapSelectorWheel(false)

    val tapButton = tempoLayout.findViewById(R.id.dialog_tap_button).asInstanceOf[View]
    tapButton.setOnClickListener( new android.view.View.OnClickListener {
      def onClick(v: View) {
	getActivity.asInstanceOf[MainActivity].onTap(v)
      }
    })

    val startButton = tempoLayout.findViewById(R.id.start_button).asInstanceOf[Button]
    startButton.setOnClickListener( new android.view.View.OnClickListener {
      def onClick(v: View) {
	getActivity.asInstanceOf[MainActivity].startChopsBuilder(tempoPicker.getValue, mMinutes)
      }
    })

    tempoLayout
  }

}

object StartingTempoDialog { def newInstance(minutes: Int, tempo: Int) = {
  val f = new StartingTempoDialog
  val args = new Bundle
  args.putInt("minutes", minutes)
  args.putInt("startTempo", tempo)
  f.setArguments(args)
  f
}}
