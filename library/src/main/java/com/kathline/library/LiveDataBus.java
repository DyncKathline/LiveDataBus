package com.kathline.library;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.ConcurrentHashMap;

public class LiveDataBus {

    private static volatile LiveDataBus instance;

    private final ConcurrentHashMap<Object, BusLiveData<Object>> mBus;
    private final ConcurrentHashMap<Object, Event> mEvents;

    private static class Event {
        private boolean key;
        private boolean isUse;

        public Event(boolean key, boolean isUse) {
            this.key = key;
            this.isUse = isUse;
        }

        public boolean isKey() {
            return key;
        }

        public void setKey(boolean key) {
            this.key = key;
        }

        public boolean isUse() {
            return isUse;
        }

        public void setUse(boolean use) {
            isUse = use;
        }
    }

    private LiveDataBus() {
        mBus = new ConcurrentHashMap<>();
        mEvents = new ConcurrentHashMap<>();
    }

    public static LiveDataBus get() {
        if (instance == null) {
            synchronized (LiveDataBus.class) {
                if (instance == null) {
                    instance = new LiveDataBus();
                }
            }
        }
        return instance;
    }

    public <T> MutableLiveData<T> subscribe(Object eventKey) {
        String key = eventKey.toString();
        if (mBus.containsKey(key) && mBus.get(key) != null) {
            BusLiveData busLiveData = mBus.get(key);
            busLiveData.firstSubscribe = false;
        } else {
            mBus.put(key, new BusLiveData<>(key, true));
        }

        return (MutableLiveData<T>) mBus.get(key);
    }

    public <T> MutableLiveData<T> setValue(T eventKey, T value) {
        return setValue(eventKey, value, false);
    }

    /**
     *
     * @param eventKey
     * @param value
     * @param isOnce
     * @param <T>
     * @return
     */
    public <T> MutableLiveData<T> setValue(T eventKey, T value, boolean isOnce) {
        MutableLiveData<T> mutableLiveData = subscribe(eventKey);
        if(isOnce) {
            mEvents.put(eventKey, new Event(true, false));
        }
        mutableLiveData.setValue(value);
        return mutableLiveData;
    }

    public <T> MutableLiveData<T> postValue(T eventKey, T value) {
        return postValue(eventKey, value, false);
    }

    public <T> MutableLiveData<T> postValue(T eventKey, T value, boolean isOnce) {
        MutableLiveData<T> mutableLiveData = subscribe(eventKey);
        if(isOnce) {
            mEvents.put(eventKey, new Event(true, false));
        }
        mutableLiveData.postValue(value);
        return mutableLiveData;
    }

    public static class BusLiveData<T> extends MutableLiveData<T> {

        private boolean firstSubscribe;
        private String key;

        BusLiveData(String key, boolean firstSubscribe) {
            this.key = key;
            this.firstSubscribe = firstSubscribe;
        }

        @Override
        public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
            super.observe(owner, new ObserverWrapper<>(observer, key, firstSubscribe));
        }

        @Override
        public void observeForever(@NonNull Observer<? super T> observer) {
            super.observeForever(new ObserverWrapper<>(observer, key, firstSubscribe));
        }

        @Override
        public void removeObserver(@NonNull Observer<? super T> observer) {
            super.removeObserver(observer);
            if (!hasObservers()) {
                LiveDataBus.get().mBus.remove(key);
            }
        }
    }

    private static class ObserverWrapper<T> implements Observer<T> {

        private Observer<T> observer;
        private String key;

        private boolean isChanged;

        private ObserverWrapper(Observer<T> observer, String key, boolean isFirstSubscribe) {
            this.observer = observer;
            this.key = key;
            isChanged = isFirstSubscribe;
        }

        @Override
        public void onChanged(@Nullable T t) {
            if (isChanged) {
                if (observer != null) {
                    ConcurrentHashMap<Object, Event> mEvents = LiveDataBus.get().mEvents;
                    Event event = mEvents.get(key);
                    if(event != null) {
                        if(event.isKey() && !event.isUse()) {
                            event.setUse(true);
                            observer.onChanged(t);
                        }
                    }else {
                        observer.onChanged(t);
                    }
                }
            } else {
                isChanged = true;
            }
        }
    }
}
