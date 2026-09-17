package com.github.soinlee.callassistant.view;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.contract.MainContract;
import com.github.soinlee.callassistant.model.TextColorPair;
import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.db.InCall;
import com.github.soinlee.callassistant.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Native-style call-log adapter: one flat row per call with a left-hand
 * call-type icon (incoming / outgoing / missed), the number (or contact name)
 * with its attribution below, the time on the right and an expand chevron.
 */
public class CallerAdapter extends RecyclerView.Adapter<CallerAdapter.ViewHolder> {

    private static final String TAG = CallerAdapter.class.getSimpleName();

    MainContract.Presenter mPresenter;

    private List<InCall> mList;

    public CallerAdapter(MainContract.Presenter presenter) {
        mPresenter = presenter;
        mList = new ArrayList<>();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        InCall inCall = mList.get(position);
        holder.bind(inCall);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        super.onViewRecycled(holder);
    }

    public void replaceData(List<InCall> inCalls) {
        mList = inCalls;
        notifyDataSetChanged();
    }

    public InCall getItem(int position) {
        return mList.get(position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener,
            View.OnLongClickListener {

        final View main;
        final ImageView callType;
        final TextView text;
        final TextView number;
        final TextView time;
        final ImageView callBack;
        final ImageView expandArrow;
        final LinearLayout detail;
        final TextView ringTime;
        final TextView duration;
        InCall inCall;

        public ViewHolder(View view) {
            super(view);
            main = view.findViewById(R.id.main);
            callType = (ImageView) view.findViewById(R.id.call_type);
            text = (TextView) view.findViewById(R.id.text);
            number = (TextView) view.findViewById(R.id.number);
            time = (TextView) view.findViewById(R.id.time);
            callBack = (ImageView) view.findViewById(R.id.call_back);
            expandArrow = (ImageView) view.findViewById(R.id.expand_arrow);
            detail = (LinearLayout) view.findViewById(R.id.detail);
            ringTime = (TextView) view.findViewById(R.id.ring_time);
            duration = (TextView) view.findViewById(R.id.duration);

            main.setOnClickListener(this);
            main.setOnLongClickListener(this);
            callBack.setOnClickListener(v -> mPresenter.dial(inCall.getNumber()));
        }

        public void setAlpha(float alpha) {
            itemView.setAlpha(alpha);
        }

        public void bind(InCall inCall) {
            this.inCall = inCall;

            Caller caller = mPresenter.getCaller(inCall.getNumber());

            if (caller.isEmpty()) {
                if (caller.isOffline()) {
                    text.setText(caller.hasGeo() ? caller.getGeo()
                            : cardText(R.string.loading));
                } else {
                    text.setText(cardText(R.string.loading_error));
                }
                number.setText(inCall.getNumber());
            } else {
                TextColorPair t = TextColorPair.from(caller);
                text.setText(t.text);
                number.setText(TextUtils.isEmpty(
                        caller.getContactName()) ? caller.getNumber() : caller.getContactName());
            }

            // Native call-type icon + color.
            bindCallType(inCall.getCallType());

            // Time + expand chevron.
            time.setText(Utils.readableDate(inCall.getTime()));
            expandArrow.setRotation(inCall.isExpanded() ? 90f : 0f);
            detail.setVisibility(inCall.isExpanded() ? View.VISIBLE : View.GONE);

            ringTime.setText(Utils.readableTime(inCall.getRingTime()));
            duration.setText(Utils.readableTime(inCall.getDuration()));
        }

        private void bindCallType(int type) {
            int icon;
            int color;
            switch (type) {
                case 2: // CallLog.Calls.TYPE_OUTGOING
                    icon = R.drawable.ic_call_outgoing_24dp;
                    color = R.color.phone_call_outgoing;
                    break;
                case 3: // CallLog.Calls.TYPE_MISSED
                case 5: // CallLog.Calls.TYPE_REJECTED
                case 6: // CallLog.Calls.TYPE_BLOCKED
                    icon = R.drawable.ic_call_missed_24dp;
                    color = R.color.phone_call_missed;
                    break;
                case 1: // CallLog.Calls.TYPE_INCOMING
                default:
                    icon = R.drawable.ic_call_incoming_24dp;
                    color = R.color.phone_call_incoming;
                    break;
            }
            callType.setImageResource(icon);
            callType.setColorFilter(ContextCompat.getColor(callType.getContext(), color));
        }

        private String cardText(int resId) {
            return text.getContext().getString(resId);
        }

        @Override
        public void onClick(View v) {
            boolean expanded = !inCall.isExpanded();
            inCall.setExpanded(expanded);
            detail.setVisibility(expanded ? View.VISIBLE : View.GONE);
            expandArrow.setRotation(expanded ? 90f : 0f);
        }

        @Override
        public boolean onLongClick(View view) {
            mPresenter.itemOnLongClicked(inCall);
            return true;
        }
    }
}
