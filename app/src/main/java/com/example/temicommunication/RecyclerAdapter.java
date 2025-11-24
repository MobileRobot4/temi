package com.example.temicommunication;

import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

//import com.robotemi.sdk.UserInfo;

import java.util.ArrayList;

import lombok.NonNull;


public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder>{

    private ArrayList<UserInfo> userInfos;
    private ArrayList<UserInfo> guardianList;

    @NonNull
    @Override
    public RecyclerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recyclerview, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerAdapter.ViewHolder holder, int position) {
        holder.onBind(userInfos.get(position));
    }

    public void setUserInfos(ArrayList<UserInfo> list){
        this.userInfos = list;
        notifyDataSetChanged();
    }

    public void setGuardianList(ArrayList<UserInfo> list){
        this.guardianList = list;
        notifyDataSetChanged();
    }

    public ArrayList<UserInfo> getGuardianList(){
        return this.guardianList;
    }

    @Override
    public int getItemCount() {
        return userInfos.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView profile;
        TextView name;
        Button button;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            profile = (ImageView) itemView.findViewById(R.id.profile);
            name = (TextView) itemView.findViewById(R.id.name);
            button = itemView.findViewById(R.id.button);
        }

        void onBind(UserInfo item){
            profile.setImageURI(null);
            name.setText(item.getName());
            if(guardianList.contains(item)) {
                button.setBackgroundColor(Color.RED);
                button.setText("-");
            } else {
                button.setBackgroundColor(Color.BLUE);
                button.setText("+");
            }
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(button.getText().equals("+")){
                        guardianList.add(item);
                        button.setBackgroundColor(Color.RED);
                        button.setText("-");
                    } else if(button.getText().equals("-")){
                        guardianList.remove(item);
                        button.setBackgroundColor(Color.BLUE);
                        button.setText("+");
                    }
                    RecyclerAdapter.this.notifyItemChanged(getAdapterPosition());
                }
            });
        }
    }
}
