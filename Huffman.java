import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Queue;
import java.io.*;
import java.nio.ByteBuffer;

class Node implements Comparable<Node>, Serializable {

	private static final long serialVersionUID = -299482035708790407L;

	private byte b;
	private int freq;
	private final Node left, right;

	Node(byte b, int freq, Node left, Node right) {
		this.b = b;
		this.freq = freq;
		this.left = left;
		this.right = right;
	}

	public boolean isLeaf() {
		return left == null && right == null;
	}

	@Override
	public int compareTo(Node that) {
		return this.freq - that.freq;
	}

	public int getFreq() {
		return freq;
	}

	public byte getByte() {
		return b;
	}

	public Node getLeft() {
		return left;
	}

	public Node getRight() {
		return right;
	}

}

class Code {
	public int code;
	public int len;

	public Code(int code, int len) {
		this.code = code;
		this.len = len;
	}
}

public class Huffman {

	private HashMap<Byte, Integer> buildCounts(String filepath) throws IOException {

		InputStream is = new FileInputStream(filepath);
		BufferedInputStream in = new BufferedInputStream(is);

		byte[] bytes = new byte[1024 * 1024];
		long byteNum = is.available();
		long index = 0;
		HashMap<Byte, Integer> counts = new HashMap<>();

		out: while (true) {
			in.read(bytes);

			for (int i = 0; i < bytes.length; i++) {
				int f = counts.getOrDefault(bytes[i], 0);
				counts.put(bytes[i], f + 1);
				index++;
				if (index == byteNum) {
					break out;
				}
			}			
		}

		in.close();

		return counts;
	}

	private Node buildTrie(HashMap<Byte, Integer> counts) {
		Queue<Node> priorityQueue = new PriorityQueue<>();

		for (byte ch : counts.keySet()) {
			priorityQueue.add(new Node(ch, counts.get(ch), null, null));
		}

		while (priorityQueue.size() > 1) {
			Node x = priorityQueue.poll();
			Node y = priorityQueue.poll();
			Node parent = new Node((byte) 0, x.getFreq() + y.getFreq(), x, y);
			priorityQueue.add(parent);
		}

		return priorityQueue.poll();
	}

	private Code[] buildCode(Node trie) {
		Code[] table = new Code[128];
		buildCode(table, trie, 0, 0);
		return table;
	}

	private void buildCode(Code[] table, Node node, int code, int len) {
		if (node.isLeaf()) {
			table[node.getByte()] = new Code(code, len);
			return;
		}
		buildCode(table, node.getLeft(), code << 1, len + 1);
		buildCode(table, node.getRight(), (code << 1) | 1, len + 1);
	}

	public void compress(String filepath) throws IOException {

		HashMap<Byte, Integer> counts = buildCounts(filepath);
		Node trie = buildTrie(counts);
		Code[] table = buildCode(trie);

		// ???????????????????????????
		String compressFilename = "";
		String[] t = filepath.split("\\.");
		for (int i = 0; i < t.length - 1; i++) {
			compressFilename += t[i];
		}
		compressFilename += ".huffman";

		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(compressFilename));

		// ?????????????????? objBytes ???
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bo);
		oos.writeObject(trie);
		byte[] objBytes = bo.toByteArray();
		int objLen = objBytes.length;

		// ?????????????????????????????????????????????????????????write??????????????? 8 ???
		for (int i = 3; i >= 0; i--) {
			out.write((int) (objLen >> (8 * i)));
		}
		out.write(objBytes);

		// ??????????????? bit ???????????????
		for (int i = 0; i < 8; i++) {
			out.write(0);
		}

		// ??????
		InputStream is = new FileInputStream(filepath);
		BufferedInputStream in = new BufferedInputStream(is);

		byte[] bytes = new byte[1024 * 1024];
		long byteNum = is.available();
		long index = 0;

		Code code;
		int buffer = 0;
		int len = 0;
		int bitLen = 0;

		out: while (true) {
			in.read(bytes);
			for (int i = 0; i < bytes.length; i++) {
				code = table[bytes[i]];
				for (int j = code.len - 1; j >= 0; j--) {
					buffer <<= 1;
					if ((code.code & (1 << j)) != 0) {
						buffer |= 1;
					}
					len++;
					bitLen++;
					if (len == 8) {
						out.write(buffer);
						buffer = 0;
						len = 0;
					}
				}
				index++;
				if (index == byteNum) {
					if (buffer != 0)
						out.write(buffer << 8 - len);
					break out;
				}
			}
		}

		in.close();
		out.flush();
		out.close();

		RandomAccessFile rf = new RandomAccessFile(compressFilename, "rw");
		rf.seek(4 + objLen);
		rf.writeLong(bitLen);
		rf.close();

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

		BufferedInputStream in = new BufferedInputStream(new FileInputStream(filepath));
		BufferedWriter out = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(getExpendFilename(filepath))));

		// ????????????????????????
		ByteBuffer buffer;
		byte[] objLenNum = new byte[4];
		in.read(objLenNum);
		buffer = ByteBuffer.wrap(objLenNum, 0, 4);
		int objLen = buffer.getInt();

		// ?????????
		byte[] objBytes = new byte[objLen];
		in.read(objBytes);
		Node root = (Node) deserialize(objBytes);

		// ??????????????????????????? bit ??????
		byte[] dataSize = new byte[8];
		in.read(dataSize);
		buffer = ByteBuffer.wrap(dataSize, 0, 8);
		long size = buffer.getLong();

		// ?????????
		byte[] bytes = new byte[1024 * 1024];
		Node x = root;

		long n = 0;

		out: while (true) {
			in.read(bytes);
			for (int i = 0; i < bytes.length; i++) {
				for (int j = 7; j >= 0; j--) {
					if ((bytes[i] & (1 << j)) != 0) {
						x = x.getRight();
					} else {
						x = x.getLeft();
					}
					if (x.isLeaf()) {
						out.write(x.getByte());
						x = root;
					}
					n++;
					if (n == size) {
						break out;
					}
				}
			}
		}

		in.close();

		out.flush();
		out.close();
		System.out.println("expend success");
	}

	public static Object deserialize(byte[] bytes) {
		Object object = null;
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			ObjectInputStream ois = new ObjectInputStream(bis);
			object = ois.readObject();
			ois.close();
			bis.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (ClassNotFoundException ex) {
			ex.printStackTrace();
		}
		return object;
	}

	public static void main(String[] args) throws IOException {
		Huffman huffman = new Huffman();
		long t = System.currentTimeMillis();
		huffman.compress("dataset.fastq");
		System.out.println("---------------------");
		huffman.expend("dataset.huffman");
		System.out.println("time: " + (System.currentTimeMillis() - t));

		
	}
}