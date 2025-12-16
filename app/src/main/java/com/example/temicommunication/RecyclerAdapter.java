package com.example.temicommunication;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.robotemi.sdk.UserInfo;

import com.bumptech.glide.Glide;

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
        ImageButton button;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            profile = (ImageView) itemView.findViewById(R.id.profile);
            name = (TextView) itemView.findViewById(R.id.name);
            button = itemView.findViewById(R.id.button);
        }

        void onBind(UserInfo item){
            String imageUrl = item.getPicUrl();
            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .into(profile);
            name.setText(item.getName());
            if(guardianList.contains(item)) {
                button.setImageResource(R.drawable.delete);
                button.setContentDescription("-");
            } else {
                button.setImageResource(R.drawable.insert);
                button.setContentDescription("+");
            }
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(button.getContentDescription().toString().equals("+")){
                        guardianList.add(item);
                        button.setImageResource(R.drawable.delete);
                        button.setContentDescription("-");
                    } else if(button.getContentDescription().toString().equals("-")){
                        guardianList.remove(item);
                        button.setImageResource(R.drawable.insert);
                        button.setContentDescription("+");
                    }
                    RecyclerAdapter.this.notifyItemChanged(getAdapterPosition());
                }
            });
        }
    }
}
