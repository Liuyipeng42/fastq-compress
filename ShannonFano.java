import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Node implements Serializable {

	private static final long serialVersionUID = -299482035708790407L;

	private List<Byte> bytes;
	private Node left, right;

	Node() {
	}

	Node(List<Byte> bytes) {
		this.bytes = bytes;
	}

	public boolean isLeaf() {
		return left == null && right == null;
	}

	public List<Byte> getBytes() {
		return bytes;
	}

	public void setBytes(List<Byte> bytes) {
		this.bytes = bytes;
	}

	public void setLeft(Node left) {
		this.left = left;
	}

	public void setRight(Node right) {
		this.right = right;
	}

	public Node getLeft() {
		return left;
	}

	public Node getRight() {
		return right;
	}

}

class Codes{
	public Code[] codeTable;
	
	public Node codeTrie;
}

class Code {
	public int code;
	public int len;

	public Code(int code, int len) {
		this.code = code;
		this.len = len;
	}
}

public class ShannonFano {

	private Code[] buildCode(Node trie) {
		Code[] table = new Code[128];
		buildCode(table, trie, 0, 0);

		return table;
	}

	private void buildCode(Code[] table, Node node, int code, int len) {
		if (node.isLeaf()) {
			table[node.getBytes().get(0)] = new Code(code, len);
			return;
		}
		buildCode(table, node.getLeft(), code << 1, len + 1);
		buildCode(table, node.getRight(), (code << 1) | 1, len + 1);
	}

	public Codes getCodes(Map<Byte, Integer> counts) {

		// 按照出现次数从大到小排序，并返回 key
		List<Byte> list = sortByCounts(counts);

		Node trie = new Node();

		trie.setBytes(list);

		genTrie(trie, counts);

		Code[] table = buildCode(trie);

		Codes codes = new Codes();

		codes.codeTable = table;
		codes.codeTrie = trie;

		return codes;
	}

	private static List<Byte> sortByCounts(Map<Byte, Integer> counts) {

		Set<Byte> countsKeys = counts.keySet();

		List<Byte> list = new ArrayList<Byte>(countsKeys);

		Collections.sort(list, (o1, o2) -> -counts.get(o1).compareTo(counts.get(o2)));

		return list;
	}

	private static void genTrie(Node node, Map<Byte, Integer> counts) {

		List<Byte> list = node.getBytes();

		if (list.size() <= 1)
			return;

		int sum = 0;
		int fullSum = 0;
		for (byte b : list)
			fullSum += counts.get(b);

		float bestdiff = 5;
		int i = 0;

		while (i < list.size()) {
			float prediff = bestdiff;
			sum += counts.get(list.get(i)); // 计算 i 之前的所有数的和
			bestdiff = Math.abs((float) sum / fullSum - 0.5F); // 计算 和 与 0.5 的差的绝对值
			if (prediff < bestdiff) // 若绝对值比上一个绝对值大，就跳出循环
				break;
			i++;
		}

		node.setBytes(null);
		node.setLeft(new Node(new ArrayList<>(list.subList(0, i))));
		node.setRight(new Node(new ArrayList<>(list.subList(i, list.size()))));

		genTrie(node.getLeft(), counts);
		genTrie(node.getRight(), counts);
	}

	private Map<Byte, Integer> buildCounts(String filepath) throws IOException {

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

	public void compress(String filepath) throws IOException {

		Map<Byte, Integer> counts = buildCounts(filepath);
		Codes codes = getCodes(counts);

		Code[] table = codes.codeTable;
		Node trie = codes.codeTrie;

		// 获取压缩后的文件名
		String compressFilename = "";
		String[] t = filepath.split("\\.");
		for (int i = 0; i < t.length - 1; i++) {
			compressFilename += t[i];
		}
		compressFilename += ".sf";

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

		// 给压缩后的 bit 长度 占位
		for (int i = 0; i < 8; i++) {
			out.write(0);
		}

		// 压缩
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
						out.write(x.getBytes().get(0));
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

		ShannonFano shannonFano = new ShannonFano();

		long t = System.currentTimeMillis();
		shannonFano.compress("dataset.fastq");
		System.out.println("---------------------");
		shannonFano.expend("dataset.sf");
		System.out.println("time: " + (System.currentTimeMillis() - t));

	}

}
