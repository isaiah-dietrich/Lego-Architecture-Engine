import axios from 'axios'

const client = axios.create({
  baseURL: 'http://localhost:7070/api',
  timeout: 10_000,
})

export default client
