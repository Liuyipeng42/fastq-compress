import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Queue;
import java.io.*;
import java.nio.ByteBuffer;

class Node implements Comparable<Node>, Serializable {

	private static final long serialVersionUID = -299482035708790407L;

	private byte ch;
	private int freq;
	private final Node left, right;

	Node(byte ch, int freq, Node left, Node right) {
		this.ch = ch;
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

	public byte getCh() {
		return ch;
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

	private Node trie;

	private Code[] symbolTable;

	private HashMap<Byte, Integer> freq;

	private void buildFreq(String filepath) throws IOException {

		InputStream is = new FileInputStream(filepath);
		BufferedInputStream in = new BufferedInputStream(is);

		byte[] bytes = new byte[1024 * 1024 * 8];
		long byteNum = is.available();
		long index = 0;
		freq = new HashMap<>();

		while (true) {
			in.read(bytes);

			for (int i = 0; i < bytes.length; i++) {
				int f = freq.getOrDefault(bytes[i], 0);
				freq.put(bytes[i], f + 1);
				index++;
				if (index == byteNum) {
					break;
				}
			}
			if (index == byteNum) {
				break;
			}
		}

		in.close();
	}

	private void buildTrie() {
		Queue<Node> priorityQueue = new PriorityQueue<>();

		for (byte ch : freq.keySet()) {
			priorityQueue.add(new Node(ch, freq.get(ch), null, null));
		}

		while (priorityQueue.size() > 1) {
			Node x = priorityQueue.poll();
			Node y = priorityQueue.poll();
			Node parent = new Node((byte) 0, x.getFreq() + y.getFreq(), x, y);
			priorityQueue.add(parent);
		}

		this.trie = priorityQueue.poll();
	}

	private void buildCode() {
		symbolTable = new Code[128];
		buildCode(trie, 0, 0);
	}

	private void buildCode(Node node, int code, int len) {
		if (node.isLeaf()) {
			symbolTable[node.getCh()] = new Code(code, len);
			return;
		}
		buildCode(node.getLeft(), code << 1, len + 1);
		buildCode(node.getRight(), (code << 1) | 1, len + 1);
	}

	public void compress(String filepath) throws IOException {

		buildFreq(filepath);
		buildTrie();
		buildCode();

		// 获取压缩后的文件名
		String compressFilename = "";
		String[] t = filepath.split("\\.");
		for (int i = 0; i < t.length - 1; i++) {
			compressFilename += t[i];
		}
		compressFilename += ".huffman";

		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(compressFilename));

		// 将树序列化到 objBytes 中
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bo);
		oos.writeObject(trie);
		byte[] objBytes = bo.toByteArray();
		int objLen = objBytes.length;

		// 将序列化的树的数据长度和数据写入文件，write每次只写低 8 位
		for (int i = 3; i >= 0; i--) {
			out.write((int) (objLen >> (8 * i)));
		}
		out.write(objBytes);

		// 将压缩后的 bit 数写入文件
		for (int i = 0; i < 8; i++) {
			out.write(0);
		}

		// 压缩
		InputStream is = new FileInputStream(filepath);
		BufferedInputStream in = new BufferedInputStream(is);

		byte[] bytes = new byte[1024 * 1024 * 8];
		long byteNum = is.available();
		long index = 0;

		Code code;
		int buffer = 0;
		int len = 0;
		int bitLen = 0;

		while (true) {
			in.read(bytes);
			for (int i = 0; i < bytes.length; i++) {
				code = symbolTable[bytes[i]];
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
					break;
				}
			}

			if (index == byteNum) {
				if (buffer != 0)
					out.write(buffer << 8 - len);
				break;
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

		// 读取树对象的长度
		ByteBuffer buffer;
		byte[] objLenNum = new byte[4];
		in.read(objLenNum);
		buffer = ByteBuffer.wrap(objLenNum, 0, 4);
		int objLen = buffer.getInt();

		// 读取树
		byte[] objBytes = new byte[objLen];
		in.read(objBytes);
		Node root = (Node) deserialize(objBytes);

		// 读取压缩数据的长度 bit 位数
		byte[] dataSize = new byte[8];
		in.read(dataSize);
		buffer = ByteBuffer.wrap(dataSize, 0, 8);
		long size = buffer.getLong();

		// 解压缩
		StringBuilder expended = new StringBuilder("");
		byte[] bytes = new byte[1024 * 1024 * 8];
		Node x = root;

		long n = 0;

		while (true) {
			in.read(bytes);
			for (int i = 0; i < bytes.length; i++) {
				for (int j = 7; j >= 0; j--) {
					if ((bytes[i] & (1 << j)) != 0) {
						x = x.getRight();
					} else {
						x = x.getLeft();
					}
					if (x.isLeaf()) {
						expended.append((char) x.getCh());
						x = root;
					}
					n++;
					if (n == size) {
						break;
					}
				}
				if (n == size) {
					break;
				}
			}
			if (n == size) {
				break;
			}
		}

		in.close();

		BufferedWriter out = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(getExpendFilename(filepath))));
		out.write(expended.toString());

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

	public void printCodes() {
		String result = "";
		for (int i = 0; i < symbolTable.length; i++) {
			result = "";
			if (symbolTable[i] != null) {
				int a = symbolTable[i].code;

				for (int j = 0; j < symbolTable[i].len; j++) {
					int c = a;
					a = a >> 1;
					a = a << 1;
					if (a == c) {
						result = "0" + result;
					} else {
						result = "1" + result;
					}
					a = a >> 1;
				}
			}
			System.out.println(i + " " + result);
		}
	}

	public static void pre(Node root) {
		if (root.isLeaf()) {
			System.out.print(root.getCh());
			return;
		}

		pre(root.getLeft());
		pre(root.getRight());
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