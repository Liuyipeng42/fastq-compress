import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class BinRLE {

	public void compress(String filepath, int codeLen) throws IOException {

		int max = (int) Math.pow(2, codeLen) - 1;

		File file = new File(filepath);
		long byteNum = file.length() + 1;
		byte[] data = new byte[1024 * 1024 * 8];

		String compressFilename = "";
		String[] t = filepath.split("\\.");
		for (int i = 0; i < t.length - 1; i++) {
			compressFilename += t[i];
		}
		compressFilename += ".binrle";

		BufferedInputStream in = new BufferedInputStream(new FileInputStream(filepath));
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(compressFilename));

		int index = 0;
		int present = 0;
		int old = 0;
		int cnt = 0;
		int buffer = 0;
		int len = 0;
		boolean first = true;

		out: while (true) {
			in.read(data);
			for (int i = 0; i < 1024 * 1024 * 8; i++) {
				for (int j = 7; j >= 0; j--) {

					present = ((data[i] & (1 << j)) != 0) ? 1 : 0;
					// 在文件中写入第一个bit是1还是0
					if (first) {
						out.write(present);
						first = false;
					}

					if (present != old || index == byteNum - 1) {

						boolean one = true;
						while (cnt > 0) {
							buffer <<= codeLen;
							len += codeLen;
							if (one) {
								if (cnt > max) {
									buffer |= max;
									one = !one;
								} else {
									buffer |= cnt;
								}
								cnt -= max;
							} else {
								one = !one;
							}
							if (len == 8) {
								out.write(buffer);
								buffer = 0;
								len = 0;
							}
						}

						old = present;
						cnt = 0;
					}

					cnt++;
				}

				index++;
				if (index == byteNum) {
					break out;
				}
			}
		}

		in.close();
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

	public void expend(String filepath, int codeLen) throws IOException {

		File file = new File(filepath);
		long byteNum = file.length() + 1;

		byte[] bytes = new byte[1024 * 1024];
		byte[] t = new byte[1];
		int present;

		BufferedInputStream in = new BufferedInputStream(new FileInputStream(filepath));
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getExpendFilename(filepath)));

		in.read(t);
		present = t[0];
		byteNum--;

		long index = 0;
		int preNum = 0;
		int num = 0;
		int cnt = 0;
		int buffer = 0;
		int len = 0;

		out: while (true) {
			in.read(bytes);
			for (int i = 0; i < 1024 * 1024 * 8; i++) {
				for (int j = 0; j < 8 / codeLen; j++) {
					preNum = num;
					num = ((bytes[i] << (codeLen * j)) & 0xff) >>> (8 - codeLen);
					cnt += preNum;
					if (preNum != 0 && num != 0) {
						for (int k = 0; k < cnt; k++) {
							buffer <<= 1;
							buffer |= present;
							len++;
							if (len == 8) {
								out.write(buffer);
								buffer = 0;
								len = 0;
							}
						}
						present = present == 1 ? 0 : 1;
						cnt = 0;
					}
				}

				index++;
				if (index == byteNum) {
					break out;
				}
			}
		}

		for (int k = 0; k < num; k++) {
			buffer <<= 1;
			buffer |= present;
			len++;
			if (len == 8) {
				out.write(buffer);
				buffer = 0;
				len = 0;
			}
		}

		in.close();
		out.close();

		System.out.println("expend success");
	}

	public static void main(String[] args) throws IOException {

		int codeLen = 2;
		BinRLE rle = new BinRLE();
		long t = System.currentTimeMillis();
		rle.compress("dataset.fastq", codeLen);
		System.out.println("-------------------------");
		rle.expend("dataset.binrle", codeLen);
		System.out.println("time: " + (System.currentTimeMillis() - t));

	}

}
