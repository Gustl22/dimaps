package org.oscim.app.holder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.github.johnkil.print.PrintView;
import com.unnamed.b.atv.model.TreeNode;

import org.oscim.app.R;

/**
 * Created by Bogdan Melnychuk on 2/15/15.
 */
public class SelectableItemHolder extends TreeNode.BaseNodeViewHolder<AreaFileInfo> {
    private TextView tvValue;
    private TextView tvSize;
    private TextView tvCountry;
    private TextView tvContinent;
    private CheckBox nodeSelector;

    public SelectableItemHolder(Context context) {
        super(context);
    }

    @Override
    public View createNodeView(final TreeNode node, AreaFileInfo value) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View view = inflater.inflate(R.layout.layout_selectable_item, null, false);

        tvValue = (TextView) view.findViewById(R.id.node_value);
        tvSize = (TextView) view.findViewById(R.id.node_filesize);
        tvCountry = (TextView) view.findViewById(R.id.node_country);
        tvContinent = (TextView) view.findViewById(R.id.node_continent);
        if(value.getCountry() != null){
            tvValue.setText(value.getRegion());
            tvCountry.setText(value.getCountry());
            tvContinent.setText(value.getContinent());
        } else
        tvValue.setText(value.getText());
        tvSize.setText(value.getSize());

        final PrintView iconView = (PrintView) view.findViewById(R.id.icon);
        iconView.setIconText(context.getResources().getString(value.icon));
        iconView.setIconColorRes(R.color.colorAccent);

        nodeSelector = (CheckBox) view.findViewById(R.id.node_selector);
        nodeSelector.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                node.setSelected(isChecked);
            }
        });
        nodeSelector.setChecked(node.isSelected());

//        if (node.isLastChild()) {
//            view.findViewById(R.id.bot_line).setVisibility(View.INVISIBLE);
//        }

        return view;
    }


    @Override
    public void toggleSelectionMode(boolean editModeEnabled) {
        nodeSelector.setVisibility(editModeEnabled ? View.VISIBLE : View.GONE);
        nodeSelector.setChecked(mNode.isSelected());
    }
}
