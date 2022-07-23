import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class RLE {

	private int eBitLen = 3;
	private int eLenMax = (int) Math.pow(2, eBitLen - 1) - 1;

	private int neBitLen = 4;
	private int neLenMax = (int) Math.pow(2, neBitLen - 1) - 1;

	private int buffer;
	private int len = 0;

	public String binToString(int b, int len) {
		String result = "";
		int a = b;

		for (int j = 0; j < len; j++) {
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

		return result;
	}

	private void writeEqual(byte b, int cnt, BufferedOutputStream out) throws IOException {
		// System.out.println((char) b + " " + cnt + " ");

		while (cnt > 0) {

			for (int i = 0; i < eBitLen + 8; i++) {
				buffer <<= 1;
				len++;
				if (i < eBitLen) {
					if (i == 0)
						buffer |= 1;
					else {
						if (cnt > eLenMax)
							buffer |= 1;
						else
							buffer |= (cnt & (1 << (eBitLen - 1 - i))) != 0 ? 1 : 0;
					}
				} else
					buffer |= (b & (1 << (eBitLen + 7 - i))) != 0 ? 1 : 0;

				if (len == 8) {
					out.write(buffer);
					buffer = 0;
					len = 0;
				}
			}

			cnt -= eLenMax;
		}

	}

	private void writeNotEqual(byte[] bytes, int cnt, BufferedOutputStream out) throws IOException {
		// for (int j = 0; j < cnt; j++)
		// 	System.out.print((char) bytes[j]);
		// System.out.println(" " + cnt + " ");

		int index = 0;
		int maxIndex = 0;

		while (cnt > 0) {

			for (int i = 0; i < neBitLen; i++) {
				buffer <<= 1;
				len ++;
				if (i > 0){
					if (cnt > neLenMax)
						buffer |= 1;
					else
						buffer |= (cnt & (1 << (neBitLen - 1 - i))) != 0 ? 1 : 0;
				}
				if (len == 8) {
					out.write(buffer);
					buffer = 0;
					len = 0;
				}
			}

			if (cnt > neLenMax)
				maxIndex = neLenMax;
			else
				maxIndex = cnt;
			
			for (int i = 0; i < maxIndex; i++) {
				for (int j = 7; j >= 0; j--) {
					buffer <<= 1;
					len ++;
					buffer |= (bytes[index] & (1 << j)) != 0 ? 1 : 0;
					if (len == 8) {
						out.write(buffer);
						buffer = 0;
						len = 0;
					}
				}
				index++;
			}

			cnt -= neLenMax;
		}
	}

	public void compress(String filepath) throws IOException {

		// 获取压缩后的文件名
		String compressFilename = "";
		String[] t = filepath.split("\\.");
		for (int i = 0; i < t.length - 1; i++) {
			compressFilename += t[i];
		}
		compressFilename += ".rle";

		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(compressFilename));

		InputStream is = new FileInputStream(filepath);
		BufferedInputStream in = new BufferedInputStream(is);

		byte[] bytes = new byte[1024 * 1024];
		long byteNum = is.available();
		long index = 0;

		int cnt = 0;
		boolean equal = false;

		byte[] charBuffer = new byte[255];

		byte now = 0;
		byte next;

		boolean tail = false;
		boolean start = false;

		out: while (true) {
			in.read(bytes);

			for (int i = 0; i < bytes.length; i++) {

				if (i == bytes.length - 1)
					tail = true;

				if (!tail) {
					now = bytes[i];
					next = bytes[i + 1];
				} else {
					if (!start) {
						now = bytes[i];
						start = true;
						continue;
					} else {
						next = bytes[i];
						i --;
						tail = false;
						start = false;
					}
				}

				cnt++;
				if (now != next) {
					if (equal && cnt > 0) {
						writeEqual(now, cnt, out);
						cnt = 0;
					}

					if (!equal)
						charBuffer[cnt - 1] = now;

					equal = false;
				} else {
					if (!equal && cnt - 1 > 0) {
						writeNotEqual(charBuffer, cnt - 1, out);
						charBuffer = new byte[255];
						cnt = 1;
					}
					equal = true;
				}

				index++;
				if (index == byteNum) {
					if (cnt > 0 && !equal)
						writeNotEqual(charBuffer, cnt, out);
					break out;
				}
			}
		}

		if (len != 0) {
			out.write(buffer << 8 - len);
		}

		in.close();
		out.close();

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

		System.out.println(getExpendFilename(filepath));
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getExpendFilename(filepath)));

		FileInputStream is = new FileInputStream(filepath);
		int byteNum = is.available();
		BufferedInputStream in = new BufferedInputStream(is);

		byte[] bytes = new byte[1024 * 1024];
		int index = 0;
		boolean bit = false;
		int cnt = 0;
		int bitLen = 0;
		int cntLen = 0;
		int charLen = 0;
		int buffer = 0;
		int blen = 0;

		out: while (true) {
			in.read(bytes);
			for (int i = 0; i < bytes.length; i++) {
				// System.out.println(binToString(bytes[i], 8));
				for (int j = 7; j >= 0; j--) {
					bit = (bytes[i] & (1 << j)) != 0 ? true : false;
					if (bitLen == 0) {
						if (bit)
							bitLen = eBitLen - 1;
						else
							bitLen = neBitLen - 1;
					} else {
						if (cntLen < bitLen) {
							cnt <<= 1;
							cnt |= bit ? 1 : 0;
							cntLen++;
						} else {
							buffer <<= 1;
							buffer |= bit ? 1 : 0;
							blen++;
							if (bitLen == eBitLen - 1) {
								if (blen == 8) {
									for (int k = 0; k < cnt; k++)
										out.write(buffer);
									cnt = 0;
									bitLen = 0;
									cntLen = 0;
									buffer = 0;
									blen = 0;
								}
							} else {
								if (charLen < cnt * 8) {
									charLen++;
									if (blen == 8) {
										out.write(buffer);
										buffer = 0;
										blen = 0;
									}
								} else {
									cnt = 0;
									bitLen = 0;
									cntLen = 0;
									charLen = 0;
									buffer = 0;
									blen = 0;
									j++;
								}
							}
						}

					}

				}

				index++;
				if (index == byteNum) {
					break out;
				}
			}
		}

		in.close();
		out.close();
	}

	public static void main(String[] args) throws IOException {
		RLE rle = new RLE();
		long t = System.currentTimeMillis();
		String file = "dataset";
		rle.compress(file + ".fastq");
		rle.expend(file + ".rle");
		System.out.println("time: " + (System.currentTimeMillis() - t));

	}
}
