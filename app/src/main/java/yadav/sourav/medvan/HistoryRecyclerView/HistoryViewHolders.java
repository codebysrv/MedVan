package yadav.sourav.medvan.HistoryRecyclerView;

import android.content.Intent;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import yadav.sourav.medvan.HistorySingleActivity;
import yadav.sourav.medvan.R;

public class HistoryViewHolders extends RecyclerView.ViewHolder implements View.OnClickListener{

    public TextView rideId;
    public TextView time;
    public HistoryViewHolders(View itemView) {
        super(itemView);
        itemView.setOnClickListener(this);

        rideId = (TextView) itemView.findViewById(R.id.rideId);
        time = (TextView) itemView.findViewById(R.id.time);
    }


    @Override
    public void onClick(View v) {
        Intent intent = new Intent(v.getContext(), HistorySingleActivity.class);
        Bundle b = new Bundle();
        b.putString("rideId", rideId.getText().toString());
        intent.putExtras(b);
        v.getContext().startActivity(intent);
    }
}