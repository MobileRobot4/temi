package com.example.temicommunication;

public class ResultCodeBuilder {
    private boolean setGuardian;
    private boolean isExercise;
    private boolean isSleep;
    private boolean calHeartRate;

    public ResultCodeBuilder setGuardian(boolean setGuardian){
        this.setGuardian = setGuardian;
        return this;
    }

    public ResultCodeBuilder isExercise(boolean isExercise){
        this.isExercise = isExercise;
        return this;
    }

    public ResultCodeBuilder isSleep(boolean isSleep){
        this.isSleep = isSleep;
        return this;
    }

    public ResultCodeBuilder calHeartRate(boolean calHeartRate){
        this.calHeartRate = calHeartRate;
        return this;
    }

    public int build(){
        int code = 0;
        if(setGuardian){
            code += 8;
        }
        if(isExercise){
            code += 4;
        }
        if(isSleep){
            code += 2;
        }
        if(calHeartRate){
            code += 1;
        }
        return code;
    }
}
