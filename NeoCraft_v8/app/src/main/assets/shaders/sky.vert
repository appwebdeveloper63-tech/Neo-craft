// NeoCraft v4 — Sky Vertex Shader
precision mediump float;
attribute vec3 a_Position;
uniform mat4   u_MVP;
varying vec3   v_Dir;
varying float  v_Up;
void main() {
    gl_Position = u_MVP * vec4(a_Position * 500.0, 1.0);
    gl_Position.z = gl_Position.w;  // always at far plane
    v_Dir = a_Position;
    v_Up  = a_Position.y;
}
