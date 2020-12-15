package com.kathline.library;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LiveDataBus.get()
 *         .<String>subscribe("key_test")
 *         .observe(this, new Observer<String>() {
 *             @Override
 *             public void onChanged(@Nullable String s) {
 *             }
 *         });
 * LiveDataBus.get().with("key_test").setValue(s);
 */
public final class LiveDataBus {

    private final ConcurrentHashMap<Object, BusMutableLiveData<Object>> mBus;
    private final ConcurrentHashMap<Object, Event> mEvents;

    private static class Event {
        private boolean isUse;

        public Event(boolean isUse) {
            this.isUse = isUse;
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

    private volatile static LiveDataBus mInstance;

    public static LiveDataBus get() {
        if (mInstance == null) {
            synchronized (LiveDataBus.class) {
                if (mInstance == null) {
                    mInstance = new LiveDataBus();
                }
            }
        }
        return mInstance;
    }

    public synchronized <T> MutableLiveData<T> subscribe(Object key) {
        if (!mBus.containsKey(key)) {
            mBus.put(key, new BusMutableLiveData<>(key));
        }
        return (MutableLiveData<T>) mBus.get(key);
    }

    public <T> MutableLiveData<T> setValue(T eventKey, T value, boolean isOnce) {
        MutableLiveData<T> mutableLiveData = subscribe(eventKey);
        if(isOnce) {
            mEvents.put(eventKey, new Event(false));
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
            mEvents.put(eventKey, new Event(false));
        }
        mutableLiveData.postValue(value);
        return mutableLiveData;
    }

    private static class BusMutableLiveData<T> extends MutableLiveData<T> {

        @NonNull
        private final Object key;
        private Map<Observer, Observer> observerMap = new HashMap<>();


        private BusMutableLiveData(Object key) {
            this.key = key;
        }

        @Override
        public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
            SafeCastObserver<T> safeCastObserver = new SafeCastObserver<>(observer, key);
            //保存LifecycleOwner的当前状态
            Lifecycle lifecycle = owner.getLifecycle();
            Lifecycle.State currentState = lifecycle.getCurrentState();
            int observerSize = getLifecycleObserverMapSize(lifecycle);
            boolean needChangeState = currentState.isAtLeast(Lifecycle.State.STARTED);
            if (needChangeState) {
                //把LifecycleOwner的状态改为INITIALIZED
                setLifecycleState(lifecycle, Lifecycle.State.INITIALIZED);
                //set observerSize to -1，否则super.observe(owner, observer)的时候会无限循环
                setLifecycleObserverMapSize(lifecycle, -1);
            }
            super.observe(owner, safeCastObserver);
            if (needChangeState) {
                //重置LifecycleOwner的状态
                setLifecycleState(lifecycle, currentState);
                //重置observer size，因为又添加了一个observer，所以数量+1
                setLifecycleObserverMapSize(lifecycle, observerSize + 1);
                //把Observer置为active
                hookObserverActive(safeCastObserver, true);
            }
            //更改Observer的version
            hookObserverVersion(safeCastObserver);
        }

        @Override
        public void observeForever(@NonNull Observer<? super T> observer) {
            if (!observerMap.containsKey(observer)) {
                observerMap.put(observer, new ObserverWrapper(observer, key));
            }
            super.observeForever(observerMap.get(observer));
        }

        @Override
        public void removeObserver(@NonNull Observer<? super T> observer) {
            Observer realObserver = null;
            if (observerMap.containsKey(observer)) {
                realObserver = observerMap.remove(observer);
            } else {
                realObserver = observer;
            }
            super.removeObserver(realObserver);
            if (!hasObservers()) {
                LiveDataBus.get().mBus.remove(key);
            }
        }

        private void setLifecycleObserverMapSize(Lifecycle lifecycle, int size) {
            if (lifecycle == null) {
                return;
            }
            if (!(lifecycle instanceof LifecycleRegistry)) {
                return;
            }
            try {
                Field observerMapField = LifecycleRegistry.class.getDeclaredField("mObserverMap");
                observerMapField.setAccessible(true);
                Object mObserverMap = observerMapField.get(lifecycle);
                Class<?> superclass = mObserverMap.getClass().getSuperclass();
                Field mSizeField = superclass.getDeclaredField("mSize");
                mSizeField.setAccessible(true);
                mSizeField.set(mObserverMap, size);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private int getLifecycleObserverMapSize(Lifecycle lifecycle) {
            if (lifecycle == null) {
                return 0;
            }
            if (!(lifecycle instanceof LifecycleRegistry)) {
                return 0;
            }
            try {
                Field observerMapField = LifecycleRegistry.class.getDeclaredField("mObserverMap");
                observerMapField.setAccessible(true);
                Object mObserverMap = observerMapField.get(lifecycle);
                Class<?> superclass = mObserverMap.getClass().getSuperclass();
                Field mSizeField = superclass.getDeclaredField("mSize");
                mSizeField.setAccessible(true);
                return (int) mSizeField.get(mObserverMap);
            } catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
        }

        private void setLifecycleState(Lifecycle lifecycle, Lifecycle.State state) {
            if (lifecycle == null) {
                return;
            }
            if (!(lifecycle instanceof LifecycleRegistry)) {
                return;
            }
            try {
                Field mState = LifecycleRegistry.class.getDeclaredField("mState");
                mState.setAccessible(true);
                mState.set(lifecycle, state);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Object getObserverWrapper(@NonNull Observer<T> observer) throws Exception {
            Field fieldObservers = LiveData.class.getDeclaredField("mObservers");
            fieldObservers.setAccessible(true);
            Object objectObservers = fieldObservers.get(this);
            Class<?> classObservers = objectObservers.getClass();
            Method methodGet = classObservers.getDeclaredMethod("get", Object.class);
            methodGet.setAccessible(true);
            Object objectWrapperEntry = methodGet.invoke(objectObservers, observer);
            Object objectWrapper = null;
            if (objectWrapperEntry instanceof Map.Entry) {
                objectWrapper = ((Map.Entry) objectWrapperEntry).getValue();
            }
            return objectWrapper;
        }

        private void hookObserverVersion(@NonNull Observer<T> observer) {
            try {
                //get wrapper's version
                Object objectWrapper = getObserverWrapper(observer);
                if (objectWrapper == null) {
                    return;
                }
                Class<?> classObserverWrapper = objectWrapper.getClass().getSuperclass();
                Field fieldLastVersion = classObserverWrapper.getDeclaredField("mLastVersion");
                fieldLastVersion.setAccessible(true);
                //get livedata's version
                Field fieldVersion = LiveData.class.getDeclaredField("mVersion");
                fieldVersion.setAccessible(true);
                Object objectVersion = fieldVersion.get(this);
                //set wrapper's version
                fieldLastVersion.set(objectWrapper, objectVersion);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void hookObserverActive(@NonNull Observer<T> observer, boolean active) {
            try {
                //get wrapper's version
                Object objectWrapper = getObserverWrapper(observer);
                if (objectWrapper == null) {
                    return;
                }
                Class<?> classObserverWrapper = objectWrapper.getClass().getSuperclass();
                Field mActive = classObserverWrapper.getDeclaredField("mActive");
                mActive.setAccessible(true);
                mActive.set(objectWrapper, active);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class ObserverWrapper<T> implements Observer<T> {

        @NonNull
        private final Observer<T> observer;
        private Object key;

        ObserverWrapper(@NonNull Observer<T> observer, Object key) {
            this.observer = observer;
            this.key = key;
        }

        @Override
        public void onChanged(@Nullable T t) {
            if (isCallOnObserve()) {
                return;
            }
            try {
                if (observer != null) {
                    ConcurrentHashMap<Object, Event> mEvents = LiveDataBus.get().mEvents;
                    Event event = mEvents.get(key);
                    if(event != null) {
                        if(!event.isUse()) {
                            event.setUse(true);
                            observer.onChanged(t);
                        }
                    }else {
                        observer.onChanged(t);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private boolean isCallOnObserve() {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            if (stackTrace != null && stackTrace.length > 0) {
                for (StackTraceElement element : stackTrace) {
                    if ("android.arch.lifecycle.LiveData".equals(element.getClassName()) &&
                            "observeForever".equals(element.getMethodName())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static class SafeCastObserver<T> implements Observer<T> {

        @NonNull
        private final Observer<? super T> observer;
        private Object key;

        SafeCastObserver(@NonNull Observer<? super T> observer, Object key) {
            this.observer = observer;
            this.key = key;
        }

        @Override
        public void onChanged(@Nullable T t) {
            try {
                ConcurrentHashMap<Object, Event> mEvents = LiveDataBus.get().mEvents;
                Event event = mEvents.get(key);
                if(event != null) {
                    if(!event.isUse()) {
                        event.setUse(true);
                        observer.onChanged(t);
                    }
                }else {
                    observer.onChanged(t);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
