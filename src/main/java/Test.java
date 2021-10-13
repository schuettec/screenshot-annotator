import static java.util.Objects.nonNull;

import java.util.Collections;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.platform.unix.X11;
import com.sun.jna.platform.unix.X11.Display;
import com.sun.jna.platform.unix.X11.Window;
import com.sun.jna.platform.unix.X11.XWindowAttributes;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;

public class Test {

  public static void main(String[] args) {

    X11 x11 = X11.INSTANCE;
    Display display = x11.XOpenDisplay(null);

    Window root = x11.XRootWindow(display, 0);// x11.XDefaultRootWindow(display);
    recurse(x11, display, root, 0);

    // WindowUtils.getAllWindows(true)
    // .forEach(desktopWindow -> {
    // System.out.println("------------------------------");
    // System.out.println(desktopWindow.getFilePath());
    // System.out.println(desktopWindow.getTitle());
    // System.out.println(desktopWindow.getLocAndSize());
    // });

  }

  private static void recurse(X11 x11, Display display, Window root, int depth) {
    X11.WindowByReference windowRef = new X11.WindowByReference();
    X11.WindowByReference parentRef = new X11.WindowByReference();
    PointerByReference childrenRef = new PointerByReference();
    IntByReference childCountRef = new IntByReference();

    x11.XQueryTree(display, root, windowRef, parentRef, childrenRef, childCountRef);
    if (childrenRef.getValue() == null) {
      return;
    }

    long[] ids;

    if (Native.LONG_SIZE == Long.BYTES) {
      ids = childrenRef.getValue()
          .getLongArray(0, childCountRef.getValue());
    } else if (Native.LONG_SIZE == Integer.BYTES) {
      int[] intIds = childrenRef.getValue()
          .getIntArray(0, childCountRef.getValue());
      ids = new long[intIds.length];
      for (int i = 0; i < intIds.length; i++) {
        ids[i] = intIds[i];
      }
    } else {
      throw new IllegalStateException("Unexpected size for Native.LONG_SIZE" + Native.LONG_SIZE);
    }

    for (long id : ids) {
      if (id == 0) {
        continue;
      }
      Window window = new Window(id);
      X11.XTextProperty name = new X11.XTextProperty();
      x11.XGetWMName(display, window, name);

      XWindowAttributes attributes = new X11.XWindowAttributes();
      x11.XGetWindowAttributes(display, window, attributes);

      System.out.println(String.join("", Collections.nCopies(depth, "  ")) + name.value + " X/Y/W/H: " + attributes.x
          + "/" + attributes.y + "/" + attributes.width + "/" + attributes.height + " State: "
          + getState(x11, window, display));

      // x11.XFree(windowRef.getPointer());
      // x11.XFree(parentRef.getPointer());
      // x11.XFree(childrenRef.getPointer());
      // x11.XFree(childCountRef.getPointer());
      x11.XFree(attributes.getPointer());
      x11.XFree(name.getPointer());

      recurse(x11, display, window, depth + 1);
    }
  }

  enum State {
    MINIMIZED,
    MAXIMIZED,
    NORMAL,
    NON_VISIBLE;
  }

  static State getState(X11 x11, Window window, Display display) {

    byte[] property = getProperty(x11, window, display, X11.XA_ATOM,
        X11.INSTANCE.XInternAtom(display, "_NET_WM_STATE", false));
    if (nonNull(property)) {
      X11.Atom[] atoms = getAtomProperties(bytesToInt(property));

      boolean hidden = false;
      boolean vmax = false;
      boolean hmax = false;

      for (int i = 0; i < atoms.length; i++) {
        X11.Atom atom = atoms[i];
        if (atom == null)
          continue;

        String atomName = X11.INSTANCE.XGetAtomName(display, atom);
        if ("_NET_WM_STATE_HIDDEN".equals(atomName)) {
          hidden = true;
        } else if ("_NET_WM_STATE_MAXIMIZED_VERT".equals(atomName)) {
          vmax = true;
        } else if ("_NET_WM_STATE_MAXIMIZED_HORZ".equals(atomName)) {
          hmax = true;
        }
      }

      if (hidden)
        return State.MINIMIZED;
      else if (vmax && hmax && !hidden)
        return State.MAXIMIZED;
      else
        return State.NORMAL;
    } else {
      return State.NON_VISIBLE;
    }
  }

  private static X11.Atom[] getAtomProperties(int[] ids) {
    X11.Atom[] atoms = new X11.Atom[ids.length];
    for (int i = 0; i < ids.length; i++) {
      if (ids[i] == 0)
        continue;
      atoms[i] = new X11.Atom(ids[i]);
    }
    return atoms;
  }

  private static int[] bytesToInt(byte[] prop) {
    if (prop == null)
      return null;
    int[] res = new int[prop.length / 4];
    for (int i = 0; i < res.length; i++) {
      res[i] = ((prop[i * 4 + 3] & 0xff) << 24) | ((prop[i * 4 + 2] & 0xff) << 16) | ((prop[i * 4 + 1] & 0xff) << 8)
          | ((prop[i * 4 + 0] & 0xff));

      if (res[i] != 0)
        continue;
    }
    return res;
  }

  private static byte[] getProperty(X11 x11, X11.Window window, Display display, X11.Atom xa_prop_type,
      X11.Atom xa_prop_name) {

    X11.AtomByReference xa_ret_type_ref = new X11.AtomByReference();
    IntByReference ret_format_ref = new IntByReference();
    NativeLongByReference ret_nitems_ref = new NativeLongByReference();
    NativeLongByReference ret_bytes_after_ref = new NativeLongByReference();
    PointerByReference ret_prop_ref = new PointerByReference();

    NativeLong long_offset = new NativeLong(0);
    NativeLong long_length = new NativeLong(4096 / 4);

    /*
     * MAX_PROPERTY_VALUE_LEN / 4 explanation (XGetWindowProperty manpage):
     *
     * long_length = Specifies the length in 32-bit multiples of the data to be retrieved.
     */
    if (x11.XGetWindowProperty(display, window, xa_prop_name, long_offset, long_length, false, xa_prop_type,
        xa_ret_type_ref, ret_format_ref, ret_nitems_ref, ret_bytes_after_ref, ret_prop_ref) != X11.Success) {
      String prop_name = x11.XGetAtomName(display, xa_prop_name);
      throw new RuntimeException("Cannot get " + prop_name + " property.");
    }

    X11.Atom xa_ret_type = xa_ret_type_ref.getValue();
    Pointer ret_prop = ret_prop_ref.getValue();

    if (xa_ret_type == null) {
      // the specified property does not exist for the specified window
      return null;
    }

    if (xa_ret_type == null || xa_prop_type == null || !xa_ret_type.toNative()
        .equals(xa_prop_type.toNative())) {
      x11.XFree(ret_prop);
      String prop_name = x11.XGetAtomName(display, xa_prop_name);
      throw new RuntimeException("Invalid type of " + prop_name + " property");
    }

    int ret_format = ret_format_ref.getValue();
    long ret_nitems = ret_nitems_ref.getValue()
        .longValue();

    // null terminate the result to make string handling easier
    int nbytes;
    if (ret_format == 32)
      nbytes = Native.LONG_SIZE;
    else if (ret_format == 16)
      nbytes = Native.LONG_SIZE / 2;
    else if (ret_format == 8)
      nbytes = 1;
    else if (ret_format == 0)
      nbytes = 0;
    else
      throw new RuntimeException("Invalid return format");
    int length = Math.min((int) ret_nitems * nbytes, 4096);

    byte[] ret = ret_prop.getByteArray(0, length);

    x11.XFree(ret_prop);
    return ret;
  }

}
