package br.com.rubythree.arduinoblinkled;

import android.app.AlertDialog;
import android.content.Context;


public class HSVColorPickerDialog extends AlertDialog {

	public interface OnColorSelectedListener {
		/**
		 * @param color The color code selected, or null if no color. No color is only
		 * possible if {@link HSVColorPickerDialog#setNoColorButton(int) setNoColorButton()}
		 * has been called on the dialog before showing it
		 */
		public void colorSelected( Integer color );
	}
	
	public HSVColorPickerDialog(Context context, int initialColor) {
		super(context);
	}
	
}
