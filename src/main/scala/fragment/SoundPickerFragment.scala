package org.mackler.metronome

class SoundPickerFragment extends DialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    (new AlertDialogBuilder(getActivity())).
    setTitle(R.string.choose_sound).
    setItems(R.array.sound_names, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {
	val activity = getActivity.asInstanceOf[MainActivity]
	activity.runOnUiThread(new Runnable { def run { activity.setSound(which) }})
      }
    }).
    create()
  }
}
