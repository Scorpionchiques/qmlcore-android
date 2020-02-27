package com.pureqml.android.runtime;

import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.TypeConverter;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Element extends BaseObject {
    public static final String TAG = "rt.Element";

    public static final class AlreadyHasAParentException extends Exception {
        AlreadyHasAParentException() { super("AlreadyHasAParentException"); }
    };

    private Rect                _rect               = new Rect();
    protected Rect              _combinedRect       = new Rect();
    protected Rect              _lastRect           = new Rect();
    private Point               _translate;

    private   float             _opacity            = 1;
    protected boolean           _visible            = true;
    protected boolean           _globallyVisible;
    private boolean             _clip;
    protected Element           _parent;
    protected int               _z;
    protected LinkedList<Element> _children;

    private static final float  DetectionDistance = 5;
    private static final float  DetectionDistance2 = DetectionDistance * DetectionDistance;

    private boolean             _enableScrollX;
    private boolean             _enableScrollY;
    private boolean             _useScrollX;
    private boolean             _useScrollY;
    private Point               _scrollOffset;
    private Point               _motionStartPos;
    private Point               _scrollPos;
    private int                 _eventId;

    public Element(IExecutionEnvironment env) {
        super(env);
    }

    public final Rect getRect()
    {
        Rect rect = new Rect(_rect);
        if (_translate != null)
            rect.offset(_translate.x, _translate.y);
        return rect;
    }

    public final Rect getCombinedRect()
    { return _combinedRect; }
    public final Rect getLastRenderedRect()
    { return _lastRect; }
    public final boolean visible() {
        return _visible && _opacity >= PaintState.opacityThreshold;
    }

    public final boolean scrollXEnabled() { return _parent != null? _parent._enableScrollX: false; }
    public final boolean scrollYEnabled() { return _parent != null? _parent._enableScrollY: false; }

    public final int getScrollX() {
        int x = _scrollOffset != null? _scrollOffset.x: 0;
        x += _scrollPos != null? _scrollPos.x: 0;
        return x;
    }

    public final int getScrollY() {
        int y = _scrollOffset != null? _scrollOffset.y: 0;
        y += _scrollPos != null? _scrollPos.y: 0;
        return y;
    }

    public void append(BaseObject child) throws AlreadyHasAParentException {
        if (child == null)
            throw new NullPointerException("appending null element");

        if (!(child instanceof Element)) {
            Log.i(TAG, "skipping append(), not an Element instance");
            return;
        }

        Element el = (Element)child;
        if (el._parent != null)
            throw new AlreadyHasAParentException();
        el._parent = this;
        if (_children == null)
            _children = new LinkedList<Element>();

        _children.add(el);
        el.update();
    }

    public void remove() {
        if (_parent != null)
            _parent.removeChild(this);
        _parent = null;
    }

    public void discard() {
        remove();
        super.discard();
    }

    void update() {
        _env.update(this);
    }

    public void updateStyle() {}

    protected void removeChild(Element child) {
        if (_children != null)
            _children.remove(child);
        _lastRect.union(child._lastRect);
        update();
    }

    private boolean getOverflowValue(Object objValue) {
        if (!(objValue instanceof String)) {
            Log.v(TAG, "ignoring overflow: " + objValue);
            return false;
        }
        String value = (String)objValue;
        switch(value) {
            case "auto":
            case "scroll":
                _clip = true;
                return true;
            case "visible":
                _clip = false;
                return false;
            case "hidden":
                _clip = true;
                return false;
            default:
                Log.v(TAG, "ignoring overflow: " + value);
                return false;
        }
    }

    static private Pattern _transformPattern = Pattern.compile("(\\w+)\\s*\\(([-+\\d]+)\\s*(\\w*)\\s*\\)\\s*");

    private void setTransform(String value) {
        Matcher matcher = _transformPattern.matcher(value);
        while(matcher.find()) {
            try {
                String unit = matcher.group(3);
                String transform = matcher.group(1);
                if (!unit.equals("px")) {
                    Log.w(TAG, "unknown unit '" + unit + "' used for '" + transform + "', skipping");
                    continue;
                }

                int n = Integer.parseInt(matcher.group(2));

                switch (transform) {
                    case "translateX":
                        if (_translate == null)
                            _translate = new Point();
                        _translate.x = n;
                        break;
                    case "translateY":
                        if (_translate == null)
                            _translate = new Point();
                        _translate.y = n;
                        break;
                    default:
                        Log.w(TAG, "skipping transform " + transform);
                }
            } catch (Exception ex) {
                Log.e(TAG, "transform parsing failed", ex);
            }
        }
    }

    protected void setStyle(String name, Object value) {
        switch(name) {
            case "left":    { int left = TypeConverter.toInteger(value);    _rect.right += left - _rect.left; _rect.left = left; } break;
            case "top":     { int top = TypeConverter.toInteger(value);     _rect.bottom += top - _rect.top; _rect.top = top; } break;
            case "width":   { int width = TypeConverter.toInteger(value);   _rect.right = _rect.left + width; } break;
            case "height":  { int height = TypeConverter.toInteger(value);  _rect.bottom = _rect.top + height; } break;
            case "opacity":     _opacity = TypeConverter.toFloat(value); break;
            case "z-index":     _z = TypeConverter.toInteger(value); break;
            case "visibility":  _visible = value.equals("inherit") || value.equals("visible"); break;
            case "transform": setTransform(value.toString()); break;
            case "-pure-recursive-visibility": {
                boolean globallyVisible = _globallyVisible;
                boolean visible = TypeConverter.toBoolean(value);
                if (globallyVisible != visible) {
                    _globallyVisible = visible;
                    onGloballyVisibleChanged(visible);
                }
                break;
            }
            case "cursor": break; //ignoring

            case "overflow":    _enableScrollX = _enableScrollY = getOverflowValue(value); break;
            case "overflow-x":  _enableScrollX = getOverflowValue(value);  break;
            case "overflow-y":  _enableScrollY = getOverflowValue(value);  break;

            default:
                Log.w(TAG, "ignoring setStyle " + name + ": " + value);
                return;
        }
        update();
    }

    protected void onGloballyVisibleChanged(boolean value) { }

    public void style(V8Array arguments) throws Exception {
        Object arg0 = arguments.get(0);
        if (arg0 instanceof V8Object) {
            V8Object styles = (V8Object) arg0;
            for (String key : styles.getKeys())
                setStyle(key, styles.get(key));
        } else if (arguments.length() == 2) {
            Object value = arguments.get(1);
            setStyle(arguments.getString(0), TypeConverter.getValue(_env, null, value));
            if (value instanceof Releasable)
                ((Releasable)value).release();
        }
        else
            throw new Exception("invalid setStyle invocation");//fixme: leak of resources here
        if (arg0 instanceof Releasable)
            ((Releasable)arg0).release();
    }

    protected final void beginPaint() {
        _lastRect.setEmpty();
        _combinedRect.setEmpty();
    }

    protected final void endPaint() {
    }

    protected final int getBaseX() {
        return _rect.left + (_translate != null? _translate.x: 0);
    }

    protected final int getBaseY() {
        return _rect.top + (_translate != null? _translate.y: 0);
    }

    @SuppressWarnings("unchecked")
    public final void paintChildren(PaintState parent) {
        if (_children == null)
            return;

        LinkedList<Element> children = _children;
        int scrollX = -getScrollX(), scrollY = -getScrollY();

        for (Element child : children) {
            Rect childRect = child.getRect();
            PaintState state = new PaintState(parent, scrollX + child.getBaseX(), scrollY + child.getBaseY(), child._opacity);
            if (!child._visible || !state.visible())
                continue;

            boolean clip = child._clip;
            boolean paint = true;
            if (clip) {
                state.canvas.save();
                if (!state.canvas.clipRect(new Rect(state.baseX, state.baseY, state.baseX + childRect.width(), state.baseY + childRect.height())))
                    paint = false;
            }

            if (paint)
                child.paint(state);

            if (clip) {
                state.canvas.restore();
            }

            _combinedRect.union(childRect);
            _combinedRect.union(child.getCombinedRect());
            _lastRect.union(child.getLastRenderedRect());
        }
    }

    public void paint(PaintState state) {
        beginPaint();
        paintChildren(state);
        endPaint();
    }

    public Rect getScreenRect() {
        Rect rect = getRect();
        Element el = _parent;
        while(el != null) {
            Rect elRect = el.getRect();
            rect.offset(elRect.left, elRect.top);
            el = el._parent;
        }
        return rect;
    }

    public boolean sendEvent(String keyName, KeyEvent event) {
        Log.v(TAG, "sending " + keyName + " key...");
        return emitUntilTrue(null, "keydown", keyName);
    }

    public boolean sendEvent(int eventId, int x, int y, MotionEvent event) throws Exception {
        if (!_globallyVisible)
            return false;

        boolean handled = false;

        //Log.v(TAG, this + ": position " + x + ", " + y + " " + rect + ", in " + rect.contains(x, y) + ", scrollable: " + (_enableScrollX || _enableScrollY));

        if (_children != null) {
            for(int i = _children.size() - 1; i >= 0; --i) {
                Element child = _children.get(i);
                Rect childRect = child.getRect();
                int offsetX = x - getBaseX();
                int offsetY = y - getBaseY();
                if (child.sendEvent(eventId, offsetX, offsetY, event)) {
                    handled = true;
                    break;
                }
            }
        }

        if (_parent == null)
        	return handled;

        final String click = "click";

		Rect parentRect = _parent.getRect();
		Rect rect = getRect();
		int clientWidth = _combinedRect.width(), w = parentRect.width();
		int clientHeight = _combinedRect.height(), h = rect.height();
		boolean enableScrollX = scrollXEnabled() && clientWidth > w;
		boolean enableScrollY = scrollYEnabled() && clientHeight > h;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (rect.contains(x, y) && (enableScrollX || enableScrollY || hasCallbackFor(click))) {
                    if (_motionStartPos == null)
                        _motionStartPos = new Point(); //FIXME: optimise me? (unwrap to 2 int members)
                    if (_scrollPos == null)
                        _scrollPos = new Point();
                    if (_scrollOffset == null)
                        _scrollOffset = new Point();
                    _eventId = eventId;
                    _motionStartPos.x = (int)event.getX();
                    _motionStartPos.y = (int)event.getY();
                    _useScrollX = _useScrollY = false;
                    return true;
                } else
                    return handled;
            }

            case MotionEvent.ACTION_MOVE: {
                boolean handleMove = false;
                if (!handled && _eventId == eventId && (enableScrollX || enableScrollY)) {
                    int dx = (int) (event.getX() - _motionStartPos.x);
                    int dy = (int) (event.getY() - _motionStartPos.y);

                    if (!_useScrollX && !_useScrollY) {
                        float distance = (float)Math.hypot((double)dx, (double)dy);
                        if (distance >= DetectionDistance2) {
                            if (enableScrollX && enableScrollY) {
                                if (Math.abs(dx) > Math.abs(dy))
                                    _useScrollX = true;
                                else
                                    _useScrollY = true;
                            } else if (enableScrollX) {
                                _useScrollX = true;
                            } else if (enableScrollY) {
                                _useScrollY = true;
                            }
                            handleMove = true;
                        }
                    }

                    if (_useScrollX) {
						_scrollOffset.x = -dx;

						if (_scrollPos.x + _scrollOffset.x + w > clientWidth)
							_scrollOffset.x = clientWidth - w - _scrollPos.x;

						if (_scrollPos.x + _scrollOffset.x < 0)
							_scrollOffset.x = -_scrollPos.x;

						Log.v(TAG, "adjusting scrollX to " + (_scrollPos.x + _scrollOffset.x));
						update();
						handleMove = true;
                    }

                    if (_useScrollY) {
						_scrollOffset.y = -dy;

						if (_scrollPos.y + _scrollOffset.y + h > clientHeight)
							_scrollOffset.y = clientHeight - h - _scrollPos.y;

						if (_scrollPos.y + _scrollOffset.y < 0)
							_scrollOffset.y = -_scrollPos.y;

						Log.v(TAG, "adjusting scrollY to " + (_scrollPos.y + _scrollOffset.y));
						update();
						handleMove = true;
                    }
                    if (handleMove)
                        emit(null, "scroll");
                    return handleMove;
                } else
                    return handled;
            }
            case MotionEvent.ACTION_UP: {
                if (_eventId == eventId) {
                    if (_useScrollX || _useScrollY) {
						_useScrollX = _useScrollY = false;
						_scrollPos.x += _scrollOffset.x;
						_scrollPos.y += _scrollOffset.y;
						Log.d(TAG, "scrolling finished at " + _scrollOffset + ", final position: " + _scrollPos);
						_scrollOffset = null;

						emit(null, "scroll");
						update();
						return true;
					} if (handled) {
						return true;
					} else if (rect.contains(x, y) && hasCallbackFor(click)) {
                        V8Object mouseEvent = new V8Object(_env.getRuntime());
                        mouseEvent.add("offsetX", x - rect.left);
                        mouseEvent.add("offsetY", y - rect.top);
                        emit(null, click, mouseEvent);
                        mouseEvent.close();
                        return true;
                    }
                }
                return false;
            }
            default:
                return false;
        }
    }

    public void setAttribute(String name, String value) {
        Log.w(TAG, "ignoring setAttribute " + name + ", " + value);
    }

    public String getAttribute(String name) {
        Log.w(TAG, "ignoring getAttribute " + name);
        return "";
    }

    public void setProperty(String name, String value) {
        setAttribute(name, value);
    }

    public String getProperty(String name) {
        return getAttribute(name);
    }

    public void focus() {}
    public void blur() {}

    public Rect getDstRect(PaintState state) {
        Rect rect = new Rect(0, 0, _rect.width(), _rect.height());
        rect.offset(state.baseX, state.baseY);
        return rect;
    }

    static final Paint patchAlpha(Paint paint, int alpha, float opacity) {
        alpha = (int)(alpha * opacity);
        if (alpha <= 0)
            return null;

        Paint alphaPaint = new Paint(paint);
        alphaPaint.setAlpha(alpha);
        return alphaPaint;
    }
}
