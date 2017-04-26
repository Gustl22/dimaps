package org.oscim.app.route;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.johnkil.print.PrintView;
import com.woxthebox.draglistview.DragItemAdapter;

import org.oscim.app.R;
import org.oscim.app.utils.Triplet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * Created by gustl on 25.04.17.
 */

class ItemAdapter extends DragItemAdapter<Triplet<Long, String, Integer>, ItemAdapter.DefaultViewHolder> {

    private int mLayoutId;
    private int mGrabHandleId;
    private boolean mDragOnLongPress;

    ItemAdapter(ArrayList<Triplet<Long, String, Integer>> list, int layoutId, int grabHandleId, boolean dragOnLongPress) {
        mLayoutId = layoutId;
        mGrabHandleId = grabHandleId;
        mDragOnLongPress = dragOnLongPress;
        setHasStableIds(true);
        setItemList(list);
    }

    @Override
    public DefaultViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
        return new DefaultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DefaultViewHolder holder, int position) {
        final int pos = position;
        super.onBindViewHolder(holder, position);
        String text = mItemList.get(position).second;
        int imageId = mItemList.get(position).third;
        holder.mText.setText(text);
        holder.mImage.setImageResource(imageId);
        holder.itemView.setTag(mItemList.get(position));
        holder.mRemoveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mItemList.remove(pos);
                notifyDragItemRemoved(pos);
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return mItemList.get(position).first;
    }

    class DefaultViewHolder extends DragItemAdapter.ViewHolder {
        TextView mText;
        ImageView mImage;
        PrintView mRemoveBtn;

        DefaultViewHolder(final View itemView) {
            super(itemView, mGrabHandleId, mDragOnLongPress);
            mText = (TextView) itemView.findViewById(R.id.text1);
            mImage = (ImageView) itemView.findViewById(R.id.image);
            mRemoveBtn = (PrintView) itemView.findViewById(R.id.remove);
        }

        @Override
        public void onItemClicked(View view) {
//            Toast.makeText(view.getContext(), "Item clicked", Toast.LENGTH_SHORT).show();
        }

        @Override
        public boolean onItemLongClicked(View view) {
//            Toast.makeText(view.getContext(), "Item long clicked", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private Collection<DragItemsChangeListener> DragItemsListener = new HashSet<>();

    public void registerItemChangeListener(DragItemsChangeListener listener) {
        DragItemsListener.add(listener);
    }

    public void unregisterItemChangeListener(DragItemsChangeListener listener) {
        DragItemsListener.remove(listener);
    }

    public void notifyDragItemRemoved(int position) {
        for (DragItemsChangeListener dragItemsChangeListener : DragItemsListener) {
            dragItemsChangeListener.onDragItemRemoved(position);
        }
    }

    public void notifyDragItemAdded(Triplet<Long, String, Integer> item, int position) {
        for (DragItemsChangeListener dragItemsChangeListener : DragItemsListener) {
            dragItemsChangeListener.onDragItemAdded(position);
        }
    }

    public interface DragItemsChangeListener {
        void onDragItemAdded(int position);

        void onDragItemRemoved(int position);
    }
}
