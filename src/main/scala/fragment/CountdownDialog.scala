package org.mackler.metronome

class CountdownDialog extends DialogFragment {
  var mMinutes: Int = 0

  override def onCreate (savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    mMinutes = getArguments.getInt("minutes")
  }

  override def onCreateView (
    inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle
  ): View = {
    this.getDialog.setTitle(getString(R.string.set_countdown))
    val v: View = inflater.inflate(R.layout.countdown, container, false)
    val np: NumberPicker = v.findViewById(R.id.minute_count).asInstanceOf[NumberPicker]
    np.setMinValue(1)
    np.setMaxValue(60)
    np.setWrapSelectorWheel(false)
    np.setValue(mMinutes)
    val button = v.findViewById(R.id.next_button).asInstanceOf[Button]
    button.setOnClickListener( new android.view.View.OnClickListener {
      def onClick(v: View) {
	getActivity.asInstanceOf[MainActivity].showStartingTempoDialog(np.getValue)
      }
    })
    v
  }

}

object CountdownDialog { def newInstance(minutes: Int) = {
  val f = new CountdownDialog
  val args = new Bundle
  args.putInt("minutes", minutes)
  f.setArguments(args)
  f
}}
