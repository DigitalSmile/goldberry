#!/usr/bin/env python3
"""Generates the downcall holders of dev.goldberry.media.ffm.calls.

One holder class per C function, in the `:natives` idiom: a `private static
final MethodHandle FD_<symbol>` linked once, the symbol's address as a field,
and a `call` in plain Java types. The table below is the source of truth for
what is bound; the generated Java is committed, so a change here is followed by

    python3 tools/media/generate_calls.py

from the repository root and a diff review. A function on the table must be
on media/src/main/cmake/exports/goldberry-media.symbols too, or the loader
fails to find it by name.
"""
import os
# (java type, layout) pairs
P='ADDRESS'; I='JAVA_INT'; L='JAVA_LONG'; F='JAVA_FLOAT'; SZ='SIZE_T'; CL='C_LONG'
jt={'ADDRESS':'MemorySegment','JAVA_INT':'int','JAVA_LONG':'long','JAVA_FLOAT':'float','SIZE_T':'long','C_LONG':'long'}
def holder(cls, sym, proto, ret, params, variadic=None, note=None):
    # params: list of (name, layout)
    args=', '.join(f'{jt[l]} {n}' for n,l in params)
    pass_args=', '.join(['address']+[n for n,_ in params])
    lay=', '.join(l for _,l in params)
    fd=('FunctionDescriptor.ofVoid('+lay+')') if ret is None else ('FunctionDescriptor.of('+ret+(', '+lay if lay else '')+')')
    if variadic is not None:
        link=f'Downcalls.linkVariadic({fd}, {variadic})'
    else:
        link=f'Downcalls.link({fd})'
    # C long adaptation: handles with C_LONG params get adapted so Java passes long
    needs_adapt = any(l=='C_LONG' for _,l in params)
    rt = 'void' if ret is None else jt[ret]
    def wrap(text, width=92):
        words=text.split(' '); lines=[]; cur=''
        for w in words:
            if cur and len(cur)+1+len(w)>width: lines.append(cur); cur=w
            else: cur=(cur+' '+w) if cur else w
        if cur: lines.append(cur)
        return lines
    doc='\n'.join('    /// '+x for x in (wrap(f'`{proto}`')+([''] + wrap(note) if note else [])))
    body=[]
    body.append(doc)
    body.append(f'    public static final class {cls} {{')
    body.append('')
    if needs_adapt:
        positions=', '.join(str(i+1) for i,(_,l) in enumerate(params) if l=='C_LONG')
        body.append(f'        private static final MethodHandle FD_{sym} = Downcalls.cLongsAsLongs({link}, {positions});')
    else:
        body.append(f'        private static final MethodHandle FD_{sym} = {link};')
    body.append('')
    body.append('        private final MemorySegment address;')
    body.append('')
    body.append(f'        {cls}(SymbolLookup lookup) {{')
    body.append(f'            this.address = Downcalls.symbol(lookup, "{sym}");')
    body.append('        }')
    body.append('')
    body.append(f'        public {rt} call({args}) {{')
    body.append('            try {')
    if ret is None:
        body.append(f'                FD_{sym}.invokeExact({pass_args});')
    else:
        body.append(f'                return ({rt}) FD_{sym}.invokeExact({pass_args});')
    body.append('            } catch (Throwable t) {')
    body.append(f'                throw Downcalls.failure("{sym}", t);')
    body.append('            }')
    body.append('        }')
    body.append('    }')
    return '\n'.join(body)

def file(name, doc, holders):
    fields=[]
    for h in holders:
        cls=h[0]; fname=cls[0].lower()+cls[1:]
        fields.append((cls,fname))
    comps=',\n        '.join(f'{c} {f}' for c,f in fields)
    ctor=',\n                '.join(f'new {c}(lookup)' for c,f in fields)
    uses_vari=any(len(h)>5 and h[5] is not None for h in holders)
    out=[]
    out.append('package dev.goldberry.media.ffm.calls;')
    out.append('')
    out.append('import static java.lang.foreign.ValueLayout.ADDRESS;')
    layouts=set()
    for h in holders:
        if h[3]: layouts.add(h[3])
        for _,l in h[4]: layouts.add(l)
    if 'JAVA_FLOAT' in layouts: out.append('import static java.lang.foreign.ValueLayout.JAVA_FLOAT;')
    if 'JAVA_INT' in layouts: out.append('import static java.lang.foreign.ValueLayout.JAVA_INT;')
    if 'JAVA_LONG' in layouts: out.append('import static java.lang.foreign.ValueLayout.JAVA_LONG;')
    out.append('')
    out.append('import java.lang.foreign.FunctionDescriptor;')
    out.append('import java.lang.foreign.MemorySegment;')
    out.append('import java.lang.foreign.SymbolLookup;')
    out.append('import java.lang.invoke.MethodHandle;')
    out.append('')
    out.append('import dev.goldberry.media.ffm.Downcalls;')
    if 'SIZE_T' in layouts or 'C_LONG' in layouts:
        out.append('import dev.goldberry.media.ffm.NativeTypes;')
    out.append('')
    out.append('\n'.join('/// '+l for l in doc.split('\n')))
    out.append(f'public record {name}(\n        {comps}) {{')
    out.append('')
    if 'SIZE_T' in layouts:
        out.append('    private static final java.lang.foreign.ValueLayout SIZE_T = NativeTypes.SIZE_T;')
    if 'C_LONG' in layouts:
        out.append('    private static final java.lang.foreign.ValueLayout C_LONG = NativeTypes.C_LONG;')
    if 'SIZE_T' in layouts or 'C_LONG' in layouts:
        out.append('')
    out.append('    /// Binds every function above.')
    out.append('    ///')
    out.append('    /// @param lookup the loaded library')
    out.append(f'    public static {name} bind(SymbolLookup lookup) {{')
    out.append(f'        return new {name}(\n                {ctor});')
    out.append('    }')
    for h in holders:
        out.append('')
        out.append(holder(*h))
    out.append('}')
    return '\n'.join(out)+'\n'

base=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'media', 'src', 'main', 'java', 'dev', 'goldberry', 'media', 'ffm', 'calls') + os.sep
files={}
files['MediaCalls']=("""The three functions of libgoldberry-media's own (`goldberry_media.c`): the ABI
number the loader checks first, the upstream versions, and CPU present's tone
mapping.""",[
 ('Abi','goldberry_media_abi','int goldberry_media_abi(void)',I,[]),
 ('Versions','goldberry_media_versions','const char *goldberry_media_versions(void)',P,[]),
 ('ToneMap','goldberry_media_tone_map','void goldberry_media_tone_map(const uint8_t *plane0, int stride0, const uint8_t *plane1, int stride1, const uint8_t *plane2, int stride2, int format, uint8_t *dst, int dst_stride, int width, int height, int flags, const float *tables)',None,[('plane0',P),('stride0',I),('plane1',P),('stride1',I),('plane2',P),('stride2',I),('format',I),('dst',P),('dstStride',I),('width',I),('height',I),('flags',I),('tables',P)],None,'Converts an opaque picture of planes to SDR BT.709 BGRA, mapping its HDR or wide gamut through the tables `ToneCurve` builds. Strides in bytes.'),
])
files['Dav1dCalls']=("""The functions of dav1d the AV1 decoder calls: opening and closing a context,
the send-data/get-picture loop, a packet's bytes in a buffer of dav1d's own,
and releasing what was handed back.""",[
 ('DefaultSettings','dav1d_default_settings','void dav1d_default_settings(Dav1dSettings *s)',None,[('settings',P)]),
 ('Open','dav1d_open','int dav1d_open(Dav1dContext **c_out, const Dav1dSettings *s)',I,[('contextOut',P),('settings',P)],None,'0 on success, a negative `DAV1D_ERR` otherwise.'),
 ('SendData','dav1d_send_data','int dav1d_send_data(Dav1dContext *c, Dav1dData *in)',I,[('context',P),('data',P)],None,'0 when the data was taken, `DAV1D_ERR(EAGAIN)` when a picture has to be fetched first; the data is then still the caller\'s.'),
 ('GetPicture','dav1d_get_picture','int dav1d_get_picture(Dav1dContext *c, Dav1dPicture *out)',I,[('context',P),('picture',P)],None,'0 with a picture in `out`, `DAV1D_ERR(EAGAIN)` when more data is needed.'),
 ('Close','dav1d_close','void dav1d_close(Dav1dContext **c_out)',None,[('contextOut',P)]),
 ('Flush','dav1d_flush','void dav1d_flush(Dav1dContext *c)',None,[('context',P)]),
 ('DataCreate','dav1d_data_create','uint8_t *dav1d_data_create(Dav1dData *data, size_t sz)',P,[('data',P),('size',SZ)],None,'A buffer of `sz` bytes dav1d owns, which `data` now references; null when out of memory.'),
 ('DataUnref','dav1d_data_unref','void dav1d_data_unref(Dav1dData *data)',None,[('data',P)]),
 ('PictureUnref','dav1d_picture_unref','void dav1d_picture_unref(Dav1dPicture *p)',None,[('picture',P)]),
])
files['VpxCalls']=("""The functions of libvpx the VP9 and VP8 decoder calls. `vpx_codec_flags_t`
and the decode deadline are C `long`s, 4 bytes on Windows and 8 elsewhere,
so those two handles take a Java `long` and are adapted to the target's.""",[
 ('ErrToString','vpx_codec_err_to_string','const char *vpx_codec_err_to_string(vpx_codec_err_t err)',P,[('error',I)]),
 ('ErrorDetail','vpx_codec_error_detail','const char *vpx_codec_error_detail(const vpx_codec_ctx_t *ctx)',P,[('context',P)],None,'Null when there is no detail.'),
 ('Destroy','vpx_codec_destroy','vpx_codec_err_t vpx_codec_destroy(vpx_codec_ctx_t *ctx)',I,[('context',P)]),
 ('DecInitVer','vpx_codec_dec_init_ver','vpx_codec_err_t vpx_codec_dec_init_ver(vpx_codec_ctx_t *ctx, vpx_codec_iface_t *iface, const vpx_codec_dec_cfg_t *cfg, vpx_codec_flags_t flags, int ver)',I,[('context',P),('iface',P),('config',P),('flags',CL),('version',I)],None,'`ver` is `VPX_DECODER_ABI_VERSION`, which the layout file reports.'),
 ('Decode','vpx_codec_decode','vpx_codec_err_t vpx_codec_decode(vpx_codec_ctx_t *ctx, const uint8_t *data, unsigned int data_sz, void *user_priv, long deadline)',I,[('context',P),('data',P),('size',I),('userPrivate',P),('deadline',CL)]),
 ('GetFrame','vpx_codec_get_frame','vpx_image_t *vpx_codec_get_frame(vpx_codec_ctx_t *ctx, vpx_codec_iter_t *iter)',P,[('context',P),('iterator',P)],None,'Null when the last decode has no more pictures.'),
 ('Vp8Dx','vpx_codec_vp8_dx','vpx_codec_iface_t *vpx_codec_vp8_dx(void)',P,[]),
 ('Vp9Dx','vpx_codec_vp9_dx','vpx_codec_iface_t *vpx_codec_vp9_dx(void)',P,[]),
])
files['OpusCalls']=("""The functions of libopus the Opus decoder calls: the plain decoder for mono
and stereo, the multistream one for a channel mapping, and `ctl` in the two
shapes that are used, since it is variadic.""",[
 ('Strerror','opus_strerror','const char *opus_strerror(int error)',P,[('error',I)]),
 ('DecoderCreate','opus_decoder_create','OpusDecoder *opus_decoder_create(opus_int32 Fs, int channels, int *error)',P,[('sampleRate',I),('channels',I),('errorOut',P)]),
 ('DecodeFloat','opus_decode_float','int opus_decode_float(OpusDecoder *st, const unsigned char *data, opus_int32 len, float *pcm, int frame_size, int decode_fec)',I,[('decoder',P),('data',P),('length',I),('pcm',P),('frameSize',I),('decodeFec',I)],None,'The samples per channel decoded, or a negative error.'),
 ('DecoderCtl','opus_decoder_ctl','int opus_decoder_ctl(OpusDecoder *st, int request, ...)',I,[('decoder',P),('request',I)],2,'The shape with no argument: `OPUS_RESET_STATE`.'),
 ('DecoderCtlInt','opus_decoder_ctl','int opus_decoder_ctl(OpusDecoder *st, int request, opus_int32 value)',I,[('decoder',P),('request',I),('value',I)],2,'The shape with one `opus_int32`: `OPUS_SET_GAIN`.'),
 ('DecoderDestroy','opus_decoder_destroy','void opus_decoder_destroy(OpusDecoder *st)',None,[('decoder',P)]),
 ('MultistreamDecoderCreate','opus_multistream_decoder_create','OpusMSDecoder *opus_multistream_decoder_create(opus_int32 Fs, int channels, int streams, int coupled_streams, const unsigned char *mapping, int *error)',P,[('sampleRate',I),('channels',I),('streams',I),('coupledStreams',I),('mapping',P),('errorOut',P)]),
 ('MultistreamDecodeFloat','opus_multistream_decode_float','int opus_multistream_decode_float(OpusMSDecoder *st, const unsigned char *data, opus_int32 len, float *pcm, int frame_size, int decode_fec)',I,[('decoder',P),('data',P),('length',I),('pcm',P),('frameSize',I),('decodeFec',I)]),
 ('MultistreamDecoderCtl','opus_multistream_decoder_ctl','int opus_multistream_decoder_ctl(OpusMSDecoder *st, int request, ...)',I,[('decoder',P),('request',I)],2),
 ('MultistreamDecoderCtlInt','opus_multistream_decoder_ctl','int opus_multistream_decoder_ctl(OpusMSDecoder *st, int request, opus_int32 value)',I,[('decoder',P),('request',I),('value',I)],2),
 ('MultistreamDecoderDestroy','opus_multistream_decoder_destroy','void opus_multistream_decoder_destroy(OpusMSDecoder *st)',None,[('decoder',P)]),
])
files['VorbisCalls']=("""The synthesis half of libvorbis, which the Vorbis decoder calls: the three
header packets in, then one audio packet at a time through a block into the
DSP state, and the float planes out.""",[
 ('InfoInit','vorbis_info_init','void vorbis_info_init(vorbis_info *vi)',None,[('info',P)]),
 ('InfoClear','vorbis_info_clear','void vorbis_info_clear(vorbis_info *vi)',None,[('info',P)]),
 ('CommentInit','vorbis_comment_init','void vorbis_comment_init(vorbis_comment *vc)',None,[('comment',P)]),
 ('CommentClear','vorbis_comment_clear','void vorbis_comment_clear(vorbis_comment *vc)',None,[('comment',P)]),
 ('SynthesisHeaderin','vorbis_synthesis_headerin','int vorbis_synthesis_headerin(vorbis_info *vi, vorbis_comment *vc, ogg_packet *op)',I,[('info',P),('comment',P),('packet',P)]),
 ('SynthesisInit','vorbis_synthesis_init','int vorbis_synthesis_init(vorbis_dsp_state *v, vorbis_info *vi)',I,[('dsp',P),('info',P)]),
 ('SynthesisRestart','vorbis_synthesis_restart','int vorbis_synthesis_restart(vorbis_dsp_state *v)',I,[('dsp',P)]),
 ('BlockInit','vorbis_block_init','int vorbis_block_init(vorbis_dsp_state *v, vorbis_block *vb)',I,[('dsp',P),('block',P)]),
 ('BlockClear','vorbis_block_clear','int vorbis_block_clear(vorbis_block *vb)',I,[('block',P)]),
 ('DspClear','vorbis_dsp_clear','void vorbis_dsp_clear(vorbis_dsp_state *v)',None,[('dsp',P)]),
 ('Synthesis','vorbis_synthesis','int vorbis_synthesis(vorbis_block *vb, ogg_packet *op)',I,[('block',P),('packet',P)]),
 ('SynthesisBlockin','vorbis_synthesis_blockin','int vorbis_synthesis_blockin(vorbis_dsp_state *v, vorbis_block *vb)',I,[('dsp',P),('block',P)]),
 ('SynthesisPcmout','vorbis_synthesis_pcmout','int vorbis_synthesis_pcmout(vorbis_dsp_state *v, float ***pcm)',I,[('dsp',P),('pcmOut',P)],None,'The samples per channel ready; `*pcm` then points at one float plane per channel.'),
 ('SynthesisRead','vorbis_synthesis_read','int vorbis_synthesis_read(vorbis_dsp_state *v, int samples)',I,[('dsp',P),('samples',I)]),
])
files['TheoraCalls']=("""The decoder half of libtheora, which the Theora decoder calls: the three
header packets in, as `ogg_packet`s, then one frame at a time, and the picture
out as three planes the decoder keeps until its next frame.""",[
 ('InfoInit','th_info_init','void th_info_init(th_info *_info)',None,[('info',P)]),
 ('InfoClear','th_info_clear','void th_info_clear(th_info *_info)',None,[('info',P)]),
 ('CommentInit','th_comment_init','void th_comment_init(th_comment *_tc)',None,[('comment',P)]),
 ('CommentClear','th_comment_clear','void th_comment_clear(th_comment *_tc)',None,[('comment',P)]),
 ('DecodeHeaderin','th_decode_headerin','int th_decode_headerin(th_info *_info, th_comment *_tc, th_setup_info **_setup, ogg_packet *_op)',I,[('info',P),('comment',P),('setupOut',P),('packet',P)],None,'A positive number when the packet was a header and was taken, 0 when it was the first frame, a negative `TH_E…` otherwise.'),
 ('DecodeAlloc','th_decode_alloc','th_dec_ctx *th_decode_alloc(const th_info *_info, const th_setup_info *_setup)',P,[('info',P),('setup',P)],None,'Null when the headers do not describe a stream it can decode.'),
 ('SetupFree','th_setup_free','void th_setup_free(th_setup_info *_setup)',None,[('setup',P)]),
 ('DecodePacketin','th_decode_packetin','int th_decode_packetin(th_dec_ctx *_dec, const ogg_packet *_op, ogg_int64_t *_granpos)',I,[('decoder',P),('packet',P),('granuleOut',P)],None,'0 for a new picture, `TH_DUPFRAME` for a frame that repeats the last one, a negative `TH_E…` for a packet it refused.'),
 ('DecodeYcbcrOut','th_decode_ycbcr_out','int th_decode_ycbcr_out(th_dec_ctx *_dec, th_ycbcr_buffer _ycbcr)',I,[('decoder',P),('buffer',P)],None,'Fills the three planes of the last picture: the whole coded frame, top row first, at a positive stride.'),
 ('DecodeFree','th_decode_free','void th_decode_free(th_dec_ctx *_dec)',None,[('decoder',P)]),
])
files['FlacCalls']=("""The stream decoder of libFLAC, which the FLAC decoder calls. It pulls its
bytes through a read callback and hands each decoded block to a write callback,
both upcalls into Java (`FlacDecoder`).""",[
 ('NewDecoder','FLAC__stream_decoder_new','FLAC__StreamDecoder *FLAC__stream_decoder_new(void)',P,[]),
 ('DeleteDecoder','FLAC__stream_decoder_delete','void FLAC__stream_decoder_delete(FLAC__StreamDecoder *decoder)',None,[('decoder',P)]),
 ('SetMd5Checking','FLAC__stream_decoder_set_md5_checking','FLAC__bool FLAC__stream_decoder_set_md5_checking(FLAC__StreamDecoder *decoder, FLAC__bool value)',I,[('decoder',P),('value',I)]),
 ('SetMetadataIgnoreAll','FLAC__stream_decoder_set_metadata_ignore_all','FLAC__bool FLAC__stream_decoder_set_metadata_ignore_all(FLAC__StreamDecoder *decoder)',I,[('decoder',P)]),
 ('InitStream','FLAC__stream_decoder_init_stream','FLAC__StreamDecoderInitStatus FLAC__stream_decoder_init_stream(FLAC__StreamDecoder *decoder, FLAC__StreamDecoderReadCallback read_callback, FLAC__StreamDecoderSeekCallback seek_callback, FLAC__StreamDecoderTellCallback tell_callback, FLAC__StreamDecoderLengthCallback length_callback, FLAC__StreamDecoderEofCallback eof_callback, FLAC__StreamDecoderWriteCallback write_callback, FLAC__StreamDecoderMetadataCallback metadata_callback, FLAC__StreamDecoderErrorCallback error_callback, void *client_data)',I,[('decoder',P),('read',P),('seek',P),('tell',P),('length',P),('eof',P),('write',P),('metadata',P),('error',P),('clientData',P)],None,'The seek, tell, length, eof and metadata callbacks may be null.'),
 ('Finish','FLAC__stream_decoder_finish','FLAC__bool FLAC__stream_decoder_finish(FLAC__StreamDecoder *decoder)',I,[('decoder',P)]),
 ('Flush','FLAC__stream_decoder_flush','FLAC__bool FLAC__stream_decoder_flush(FLAC__StreamDecoder *decoder)',I,[('decoder',P)]),
 ('ProcessSingle','FLAC__stream_decoder_process_single','FLAC__bool FLAC__stream_decoder_process_single(FLAC__StreamDecoder *decoder)',I,[('decoder',P)],None,'Decodes one frame, or reads one metadata block, calling the callbacks. False means an error the error callback saw, or the end of the stream; `getState` says which.'),
 ('GetState','FLAC__stream_decoder_get_state','FLAC__StreamDecoderState FLAC__stream_decoder_get_state(const FLAC__StreamDecoder *decoder)',I,[('decoder',P)]),
 ('GetResolvedStateString','FLAC__stream_decoder_get_resolved_state_string','const char *FLAC__stream_decoder_get_resolved_state_string(const FLAC__StreamDecoder *decoder)',P,[('decoder',P)]),
])
yuv3=[('srcY',P),('strideY',I),('srcU',P),('strideU',I),('srcV',P),('strideV',I),('dst',P),('dstStride',I),('constants',P),('width',I),('height',I)]
yuv2=[('srcY',P),('strideY',I),('srcUv',P),('strideUv',I),('dst',P),('dstStride',I),('constants',P),('width',I),('height',I)]
yuv3a=[('srcY',P),('strideY',I),('srcU',P),('strideU',I),('srcV',P),('strideV',I),('srcA',P),('strideA',I),('dst',P),('dstStride',I),('constants',P),('width',I),('height',I),('attenuate',I)]
yuv3f=yuv3+[('filter',I)]
yuv2f=yuv2+[('filter',I)]
yuv3af=yuv3a+[('filter',I)]
sub=[('srcY',P),('strideY',I),('srcU',P),('strideU',I),('srcV',P),('strideV',I),('dstY',P),('dstStrideY',I),('dstU',P),('dstStrideU',I),('dstV',P),('dstStrideV',I),('width',I),('height',I)]
files['YuvCalls']=("""The conversions of libyuv the CPU present path uses: planar and semi-planar
YUV in 8 and 10 bits to 32-bit BGRA (libyuv's "ARGB", which is the byte order
B, G, R, A in memory), each with a matrix handed in and chroma upsampled by the
filter named, and the subsamplings to 4:2:0. The matrices are libyuv's own
constants, found by name in `YuvConstants`; the filter is `kFilterBilinear`,
read from the layout file.""",[
 ('I420ToArgbMatrixFilter','I420ToARGBMatrixFilter','int I420ToARGBMatrixFilter(const uint8_t *src_y, int src_stride_y, const uint8_t *src_u, int src_stride_u, const uint8_t *src_v, int src_stride_v, uint8_t *dst_argb, int dst_stride_argb, const struct YuvConstants *yuvconstants, int width, int height, enum FilterMode filter)',I,yuv3f,None,'`kFilterBilinear` interpolates each chroma sample at its centre, 9:3:3:1, which is what a GPU sampler does with the planes.'),
 ('I010ToArgbMatrixFilter','I010ToARGBMatrixFilter','int I010ToARGBMatrixFilter(const uint16_t *src_y, int src_stride_y, const uint16_t *src_u, int src_stride_u, const uint16_t *src_v, int src_stride_v, uint8_t *dst_argb, int dst_stride_argb, const struct YuvConstants *yuvconstants, int width, int height, enum FilterMode filter)',I,yuv3f,None,'The strides are in samples (16-bit elements), not bytes: libyuv advances a `uint16_t *` by them. A `VideoFrame` stride in bytes is halved on the way in.'),
 ('P010ToArgbMatrixFilter','P010ToARGBMatrixFilter','int P010ToARGBMatrixFilter(const uint16_t *src_y, int src_stride_y, const uint16_t *src_uv, int src_stride_uv, uint8_t *dst_argb, int dst_stride_argb, const struct YuvConstants *yuvconstants, int width, int height, enum FilterMode filter)',I,yuv2f),
 ('I420AlphaToArgbMatrixFilter','I420AlphaToARGBMatrixFilter','int I420AlphaToARGBMatrixFilter(const uint8_t *src_y, int src_stride_y, const uint8_t *src_u, int src_stride_u, const uint8_t *src_v, int src_stride_v, const uint8_t *src_a, int src_stride_a, uint8_t *dst_argb, int dst_stride_argb, const struct YuvConstants *yuvconstants, int width, int height, int attenuate, enum FilterMode filter)',I,yuv3af,None,'`attenuate` 1 premultiplies the colour by the alpha on the way out.'),
 ('SplitUvPlane','SplitUVPlane','void SplitUVPlane(const uint8_t *src_uv, int src_stride_uv, uint8_t *dst_u, int dst_stride_u, uint8_t *dst_v, int dst_stride_v, int width, int height)',None,[('srcUv',P),('strideUv',I),('dstU',P),('dstStrideU',I),('dstV',P),('dstStrideV',I),('width',I),('height',I)],None,'NV12\'s interleaved chroma as two planes, `width` being the chroma width: libyuv has no filtered NV12 conversion, so NV12 goes through the I420 one.'),
 ('I444ToI420','I444ToI420','int I444ToI420(const uint8_t *src_y, int src_stride_y, const uint8_t *src_u, int src_stride_u, const uint8_t *src_v, int src_stride_v, uint8_t *dst_y, int dst_stride_y, uint8_t *dst_u, int dst_stride_u, uint8_t *dst_v, int dst_stride_v, int width, int height)',I,sub,None,'Subsamples 4:4:4 chroma to 4:2:0, for a picture the frame contract does not name.'),
 ('I422ToI420','I422ToI420','int I422ToI420(...the same shape as I444ToI420...)',I,sub),
 ('I410ToI010','I410ToI010','int I410ToI010(const uint16_t *src_y, int src_stride_y, const uint16_t *src_u, int src_stride_u, const uint16_t *src_v, int src_stride_v, uint16_t *dst_y, int dst_stride_y, uint16_t *dst_u, int dst_stride_u, uint16_t *dst_v, int dst_stride_v, int width, int height)',I,sub,None,'The 10-bit 4:4:4 to 4:2:0 subsampling; strides in samples, as every 16-bit libyuv function counts them.'),
 ('I210ToI010','I210ToI010','int I210ToI010(...the same shape as I410ToI010...)',I,sub),
])
files['Mp3Calls']=("""The two functions of minimp3, instantiated in `goldberry_media.c` with float
output: one frame of MP3 in, up to 1152 samples a channel out.""",[
 ('Init','mp3dec_init','void mp3dec_init(mp3dec_t *dec)',None,[('decoder',P)]),
 ('DecodeFrame','mp3dec_decode_frame','int mp3dec_decode_frame(mp3dec_t *dec, const uint8_t *mp3, int mp3_bytes, mp3d_sample_t *pcm, mp3dec_frame_info_t *info)',I,[('decoder',P),('data',P),('length',I),('pcm',P),('info',P)],None,'The samples per channel decoded: 0 with `frame_bytes` > 0 means a frame was skipped, 0 with `frame_bytes` 0 means more data is needed.'),
])
for name,(doc,holders) in files.items():
    open(base+name+'.java','w').write(file(name,doc,holders))
    print(name, len(holders))
