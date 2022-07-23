import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

class Prefix {
	String str;
	int code;

	public Prefix(String str, int code) {
		this.str = str;
		this.code = code;
	}
}

class TST {

	private class Node {
		char c;
		Node left, mid, right;
		Integer val;
	}

	private Node root;

	public Prefix longestPrefixOf(String s, int beginIndex) {
		int[] endIndexAndVal = search(root, s, beginIndex, 0, 0);

		if (endIndexAndVal[0] >= beginIndex)
			return new Prefix(s.substring(beginIndex, endIndexAndVal[0] + 1), endIndexAndVal[1]);
		else
			return null;
	}

	private int[] search(Node node, String s, int index, int endIndex, int val) {

		if (node == null)
			return new int[] { endIndex, val };
		if (index == s.length())
			return new int[] { endIndex, val };

		char c = s.charAt(index);

		Node next = null;
		if (c == node.c) {
			if (node.val != null) {
				endIndex = index;
				val = node.val;
			}
			index++;
			next = node.mid;
		}
		if (c < node.c)
			next = node.left;
		if (c > node.c)
			next = node.right;

		return search(next, s, index, endIndex, val);

	}

	public Integer get(String key) {

		Node x = get(root, key, 0);

		if (x == null)
			return null;
		return x.val;
	}

	private Node get(Node x, String key, int index) {

		if (x == null)
			return null;

		char c = key.charAt(index);

		if (c < x.c)
			return get(x.left, key, index);
		else if (c > x.c)
			return get(x.right, key, index);
		else if (index < key.length() - 1)
			return get(x.mid, key, index + 1);
		else
			return x;
	}

	public void put(String key, int val) {
		root = put(root, key, val, 0);
	}

	private Node put(Node x, String key, int val, int d) {

		char c = key.charAt(d);

		if (x == null) {
			x = new Node();
			x.c = c;
		}

		if (c < x.c)
			x.left = put(x.left, key, val, d);
		else if (c > x.c)
			x.right = put(x.right, key, val, d);
		else if (d < key.length() - 1)
			x.mid = put(x.mid, key, val, d + 1);
		else
			x.val = val;
		return x;
	}

}

public class LZW {

	private String text;

	private void readFile(String filepath) throws FileNotFoundException, IOException {
		InputStream is = new FileInputStream(filepath);
		byte[] bytes = new byte[is.available()];
		is.read(bytes);
		this.text = new String(bytes);
		is.close();
	}

	public void compress(String filepath) throws IOException {
		int R = 256;
		int L = 4096;

		readFile(filepath);
		TST st = new TST();

		for (int i = 0; i < R; i++)
			st.put("" + (char) i, i);

		String compressFilename = "";
		String[] t = filepath.split("\\.");
		for (int i = 0; i < t.length - 1; i++) {
			compressFilename += t[i];
		}
		compressFilename += ".lzw";

		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(compressFilename));

		int code = R + 1;
		int beginIndex = 0;
		int buffer = 0;
		int len = 0;

		while (beginIndex < text.length()) {
			Prefix prefix = st.longestPrefixOf(text, beginIndex);
			for (int i = 11; i >= 0; i--) {
				buffer <<= 1;
				buffer |= ((prefix.code & (1 << i)) != 0) ? 1 : 0;
				len++;
				if (len == 8) {
					out.write(buffer);
					buffer = 0;
					len = 0;
				}
			}

			int plen = prefix.str.length();
			if (plen < text.length() - beginIndex && code < L)
				st.put(text.substring(beginIndex, beginIndex + plen + 1), code++);

			beginIndex += plen;
		}

		if (buffer != 0) {
			buffer <<= 4;
			out.write(buffer);
		}

		out.close();

		System.out.println("compress success");

	}

	private String getExpendFilename(String filepath) {
		String expendFilename = "";
		String[] temp = filepath.split("\\.");
		for (int i = 0; i < temp.length - 1; i++) {
			expendFilename += temp[i];
		}

		if (new File(expendFilename + ".fastq").exists()) {
			int c = 1;
			while (new File(expendFilename + c + ".fastq").exists()) {
				c += 1;
			}
			expendFilename += c + ".fastq";
		}
		return expendFilename;
	}

	public void expend(String filepath) throws IOException {

		String[] st = new String[4096];
		int nextCode = 0;
		for (nextCode = 0; nextCode < 256; nextCode++)
			st[nextCode] = "" + (char) nextCode;
		st[nextCode++] = " ";

		FileInputStream is = new FileInputStream(filepath);
		BufferedInputStream in = new BufferedInputStream(is);
		BufferedWriter out = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(getExpendFilename(filepath))));
		
		long byteNum = is.available();
		long index = 0;
		int code = 0;
		int len = 0;

		byte[] t = new byte[2];
		in.read(t);
		index = 2;

		code |= t[0];
		code <<= 4;
		code |= (t[1] >> 4) & 0xf;
		String val = st[code];

		code = t[1] & 0xf;
		len = 4;

		out: while (true) {
			byte[] bytes = new byte[1024 * 1024];
			in.read(bytes);
			for (int i = 0; i < bytes.length; i++) {
				for (int j = 1; j >= 0; j--) {
					code <<= 4;
					code |= (bytes[i] >> 4 * j) & 0xf;
					len += 4;
					if (len == 12) {
						for (int k = 0; k < val.length(); k++) 
							out.write(val.charAt(k));
						
						String s = st[code];
						if (nextCode == code)
							s = val + val.charAt(0);
						if (nextCode < 4096)
							st[nextCode++] = val + s.charAt(0);
						val = s;

						code = 0;
						len = 0;
					}
				}

				index++;
				if (index == byteNum + 2) {
					break out;
				}
			}
		}
		in.close();

		out.flush();
		out.close();

		System.out.println("expend success");

	}

	public static void main(String[] args) throws IOException {

		LZW lzw = new LZW();

		long t = System.currentTimeMillis();
		lzw.compress("dataset.fastq");
		System.out.println("------------------------");
		lzw.expend("dataset.lzw");
		System.out.println("time: " + (System.currentTimeMillis() - t));

	}

}
