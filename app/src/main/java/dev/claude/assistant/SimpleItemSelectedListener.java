package dev.claude.assistant;

import android.view.View;
import android.widget.AdapterView;

/** Kleine Adapterklasse, damit Spinner-Auswahl als Lambda behandelt werden kann. */
final class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    interface SelectionHandler {
        void selected(int position);
    }

    private final SelectionHandler handler;

    SimpleItemSelectedListener(SelectionHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        handler.selected(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}
