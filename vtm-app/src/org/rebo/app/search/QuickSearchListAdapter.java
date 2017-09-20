package org.rebo.app.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.github.johnkil.print.PrintView;

import org.rebo.app.R;

import java.util.List;

public class QuickSearchListAdapter extends ArrayAdapter<QuickSearchListItem> {
    public QuickSearchListAdapter(Context context, List<QuickSearchListItem> items) {
        super(context, 0, items);
    }


    @Override

    public View getView(int position, View convertView, ViewGroup parent) {

        // Get the data item for this position

        QuickSearchListItem item = getItem(position);

        // Check if an existing view is being reused, otherwise inflate the view

        if (convertView == null) {

            convertView = LayoutInflater.from(getContext()).inflate(R.layout.search_list_item, parent, false);

        }

        // Lookup view for data population

        TextView tvTitle = (TextView) convertView.findViewById(R.id.title);

        TextView tvSubTitle = (TextView) convertView.findViewById(R.id.subtitle);
        TextView tvDistance = (TextView) convertView.findViewById(R.id.distance);
        TextView tvTime = (TextView) convertView.findViewById(R.id.time);

        //Views
        PrintView pvMain = (PrintView) convertView.findViewById(R.id.imageView);

        // Populate the data into the template view using the data object

        tvTitle.setText(item.getName());
        tvSubTitle.setText(item.getTown());
        tvDistance.setText(item.getDistance());
        tvTime.setText("---");

        pvMain.setIconText(item.getCategoryIcon());

        // Return the completed view to render on screen

        return convertView;

    }

}
