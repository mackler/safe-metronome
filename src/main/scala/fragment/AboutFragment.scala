package org.mackler.metronome

class AboutFragment extends DialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    (new AlertDialogBuilder(getActivity())).
    setTitle(R.string.app_name).
    setMessage("By Adam Mackler\n\nAdamMackler@gmail.com").
    setPositiveButton("OK", new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {}
    }).
    create()
  }
}
